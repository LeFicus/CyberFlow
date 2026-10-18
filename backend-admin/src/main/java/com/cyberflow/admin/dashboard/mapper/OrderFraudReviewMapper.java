package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderFraudReviewMapper {
    @Select("SELECT id, user_group, shipping_email, " + OrderFraudSql.SUSPECT_SQL +
            " AS suspect FROM orders WHERE user_group=#{group} AND id=#{id} FOR UPDATE")
    Map<String, Object> lockOrder(@Param("group") String group, @Param("id") long id);

    @Insert("INSERT INTO order_fraud_reviews (user_group,order_id,email,decision,reviewer,note) " +
            "VALUES (#{group},#{id},#{email},#{decision},#{reviewer},#{note})")
    int insertReview(@Param("group") String group, @Param("id") long id, @Param("email") String email,
                     @Param("decision") String decision, @Param("reviewer") String reviewer, @Param("note") String note);

    @Select("SELECT email,decision,reviewer,note,reviewed_at FROM order_fraud_reviews " +
            "WHERE user_group=#{group} AND order_id=#{id} ORDER BY id DESC")
    List<Map<String, Object>> history(@Param("group") String group, @Param("id") long id);
}
