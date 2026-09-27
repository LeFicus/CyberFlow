package com.cyberflow.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.system.entity.SysNotificationChannel;
import com.cyberflow.admin.system.mapper.SysNotificationChannelMapper;
import com.cyberflow.admin.system.notification.NotificationPlatform;
import com.cyberflow.admin.system.notification.NotificationSecretCipher;
import com.cyberflow.admin.system.notification.NotificationWebhookClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SysNotificationService {

    private static final DateTimeFormatter TEST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String DEFAULT_TEMPLATE = "【{{title}}】\n{{content}}\n\n渠道：{{channel}}\n时间：{{time}}（北京时间）";
    private final SysNotificationChannelMapper mapper;
    private final NotificationSecretCipher cipher;
    private final NotificationWebhookClient webhookClient;

    public record ChannelRequest(String name, String platform, String webhookUrl, String messageTemplate,
                                 String signingSecret, Boolean clearSigningSecret, Boolean enabled) {}

    public record ChannelView(Long id, String name, String platform, String platformLabel,
                              boolean webhookConfigured, boolean signingSecretConfigured,
                              String messageTemplate, boolean enabled,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record TestMessageRequest(String title, String content) {}

    public List<ChannelView> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysNotificationChannel>()
                        .orderByDesc(SysNotificationChannel::getEnabled)
                        .orderByDesc(SysNotificationChannel::getUpdatedAt)
                        .orderByDesc(SysNotificationChannel::getId))
                .stream().map(this::view).toList();
    }

    @Transactional
    public ChannelView create(ChannelRequest request) {
        if (request == null) throw new IllegalArgumentException("通知机器人配置不能为空");
        String name = validateName(request.name());
        NotificationPlatform platform = NotificationPlatform.parse(request.platform());
        String webhook = validateWebhook(platform, request.webhookUrl(), true);
        String secret = normalizeSecret(request.signingSecret());

        SysNotificationChannel channel = new SysNotificationChannel();
        channel.setName(name);
        channel.setPlatform(platform.name());
        channel.setWebhookUrl(cipher.encrypt(webhook));
        channel.setSigningSecret(cipher.encrypt(secret));
        channel.setMessageTemplate(validateTemplate(request.messageTemplate()));
        channel.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        mapper.insert(channel);
        return view(requireChannel(channel.getId()));
    }

    @Transactional
    public ChannelView update(Long id, ChannelRequest request) {
        if (request == null) throw new IllegalArgumentException("通知机器人配置不能为空");
        SysNotificationChannel channel = requireChannel(id);
        String name = validateName(request.name());
        NotificationPlatform platform = NotificationPlatform.parse(request.platform());
        boolean platformChanged = !platform.name().equals(channel.getPlatform());

        boolean keepsWebhook = request.webhookUrl() == null || request.webhookUrl().isBlank();
        String webhook = keepsWebhook
                ? cipher.decrypt(channel.getWebhookUrl())
                : validateWebhook(platform, request.webhookUrl(), true);
        if (platformChanged && keepsWebhook) {
            throw new IllegalArgumentException("切换通知平台时必须重新填写 Webhook");
        }
        platform.validateWebhook(webhook);

        channel.setName(name);
        channel.setPlatform(platform.name());
        channel.setMessageTemplate(validateTemplate(request.messageTemplate()));
        if (request.webhookUrl() != null && !request.webhookUrl().isBlank()) {
            channel.setWebhookUrl(cipher.encrypt(webhook));
        }
        if (Boolean.TRUE.equals(request.clearSigningSecret())) {
            channel.setSigningSecret(null);
        } else if (request.signingSecret() != null && !request.signingSecret().isBlank()) {
            channel.setSigningSecret(cipher.encrypt(normalizeSecret(request.signingSecret())));
        }
        channel.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        mapper.updateById(channel);
        return view(requireChannel(id));
    }

    @Transactional
    public void delete(Long id) {
        requireChannel(id);
        mapper.deleteById(id);
    }

    public void sendTest(Long id, TestMessageRequest request) {
        SysNotificationChannel channel = requireChannel(id);
        NotificationPlatform platform = NotificationPlatform.parse(channel.getPlatform());
        String title = normalizeMessagePart(request == null ? null : request.title(), "CyberFlow 测试通知", 200, "通知标题");
        String content = normalizeMessagePart(request == null ? null : request.content(),
                "机器人连接正常，这是一条自定义内容测试。", 3000, "通知正文");
        String message = render(channel, platform, title, content);
        webhookClient.send(platform, cipher.decrypt(channel.getWebhookUrl()),
                cipher.decrypt(channel.getSigningSecret()), message);
    }

    /** Reusable entry point for future task and system alerts. */
    public void sendToEnabled(String message) {
        sendToEnabled("CyberFlow 通知", message);
    }

    public void sendToEnabled(String title, String content) {
        sendToEnabled(null, title, content);
    }

    /** Send only through enabled channels of the requested platform. A null platform means all platforms. */
    public void sendToEnabled(NotificationPlatform requiredPlatform, String title, String content) {
        if (content == null || content.isBlank()) return;
        String normalizedTitle = normalizeMessagePart(title, "CyberFlow 通知", 200, "通知标题");
        String normalizedContent = normalizeMessagePart(content, null, 3000, "通知正文");
        LambdaQueryWrapper<SysNotificationChannel> query = new LambdaQueryWrapper<SysNotificationChannel>()
                .eq(SysNotificationChannel::getEnabled, 1);
        if (requiredPlatform != null) query.eq(SysNotificationChannel::getPlatform, requiredPlatform.name());
        mapper.selectList(query)
                .forEach(channel -> {
                    try {
                        NotificationPlatform platform = NotificationPlatform.parse(channel.getPlatform());
                        webhookClient.send(platform, cipher.decrypt(channel.getWebhookUrl()),
                                cipher.decrypt(channel.getSigningSecret()),
                                render(channel, platform, normalizedTitle, normalizedContent));
                    } catch (RuntimeException e) {
                        // A broken channel must not prevent other configured robots from receiving an alert.
                        log.warn("Notification delivery failed: channelId={}, platform={}, reason={}",
                                channel.getId(), channel.getPlatform(), e.getMessage());
                    }
                });
    }

    private SysNotificationChannel requireChannel(Long id) {
        SysNotificationChannel channel = id == null ? null : mapper.selectById(id);
        if (channel == null) throw new IllegalArgumentException("通知机器人不存在或已删除");
        return channel;
    }

    private String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 100) throw new IllegalArgumentException("机器人名称长度须为 1–100 个字符");
        return name;
    }

    private String validateWebhook(NotificationPlatform platform, String value, boolean required) {
        String webhook = value == null ? "" : value.trim();
        if (required && webhook.isEmpty()) throw new IllegalArgumentException("请填写机器人 Webhook");
        if (webhook.length() > 1000) throw new IllegalArgumentException("Webhook 地址过长");
        platform.validateWebhook(webhook);
        return webhook;
    }

    private String normalizeSecret(String value) {
        String secret = value == null ? null : value.trim();
        if (secret != null && secret.length() > 500) throw new IllegalArgumentException("签名密钥过长");
        return secret == null || secret.isBlank() ? null : secret;
    }

    private String validateTemplate(String value) {
        String template = value == null || value.isBlank() ? DEFAULT_TEMPLATE : value.trim();
        if (template.length() > 4000) throw new IllegalArgumentException("通知模板不能超过 4000 个字符");
        return template;
    }

    private String normalizeMessagePart(String value, String fallback, int maxLength, String label) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (normalized == null || normalized.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
        return normalized;
    }

    String render(SysNotificationChannel channel, NotificationPlatform platform, String title, String content) {
        String timestamp = TEST_TIME.format(java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
        return validateTemplate(channel.getMessageTemplate())
                .replace("{{title}}", title)
                .replace("{{content}}", content)
                .replace("{{time}}", timestamp)
                .replace("{{channel}}", channel.getName())
                .replace("{{platform}}", platform.label());
    }

    private ChannelView view(SysNotificationChannel channel) {
        NotificationPlatform platform = NotificationPlatform.parse(channel.getPlatform());
        return new ChannelView(channel.getId(), channel.getName(), platform.name(), platform.label(),
                channel.getWebhookUrl() != null && !channel.getWebhookUrl().isBlank(),
                channel.getSigningSecret() != null && !channel.getSigningSecret().isBlank(),
                validateTemplate(channel.getMessageTemplate()),
                channel.getEnabled() != null && channel.getEnabled() == 1,
                channel.getCreatedAt(), channel.getUpdatedAt());
    }
}
