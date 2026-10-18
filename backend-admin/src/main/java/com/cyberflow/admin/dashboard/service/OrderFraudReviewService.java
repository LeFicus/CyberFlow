package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.OrderFraudReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderFraudReviewService {
    private final OrderFraudReviewMapper mapper;

    @Transactional
    public void review(String group, long id, String expectedEmail, String decision, String note, String reviewer) {
        if (!Set.of("APPROVED", "REJECTED", "PENDING").contains(decision == null ? "" : decision)) {
            throw new IllegalArgumentException("审核结果无效");
        }
        if (note == null || note.isBlank() || note.trim().length() > 500) {
            throw new IllegalArgumentException("请填写 1 至 500 字的审核意见");
        }
        var order = mapper.lockOrder(group, id);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        String email = normalize(order.get("shipping_email"));
        if (!email.equals(normalize(expectedEmail))) {
            throw new IllegalArgumentException("订单邮箱已变化，请刷新后重新审核");
        }
        if (!Boolean.TRUE.equals(order.get("suspect")) && !"1".equals(String.valueOf(order.get("suspect")))) {
            throw new IllegalArgumentException("该订单未命中疑似刷单规则");
        }
        mapper.insertReview(group, id, email, decision, reviewer, note.trim());
    }

    private static String normalize(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }
}
