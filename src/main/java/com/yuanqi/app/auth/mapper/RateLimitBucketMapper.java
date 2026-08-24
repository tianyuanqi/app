package com.yuanqi.app.auth.mapper;

import com.yuanqi.app.auth.entity.RateLimitBucket;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RateLimitBucketMapper {
    @Select("SELECT * FROM rate_limit_bucket WHERE bucket_key=#{bucketKey} AND action_type=#{actionType} " +
            "AND window_started_at=#{windowStartedAt} AND window_seconds=#{windowSeconds} FOR UPDATE")
    RateLimitBucket findForUpdate(@Param("bucketKey") String bucketKey,
                                  @Param("actionType") String actionType,
                                  @Param("windowStartedAt") LocalDateTime windowStartedAt,
                                  @Param("windowSeconds") int windowSeconds);

    @Insert("INSERT INTO rate_limit_bucket(bucket_key,action_type,window_started_at,window_seconds,request_count,updated_at) " +
            "VALUES(#{bucketKey},#{actionType},#{windowStartedAt},#{windowSeconds},1,#{now})")
    int insert(@Param("bucketKey") String bucketKey, @Param("actionType") String actionType,
               @Param("windowStartedAt") LocalDateTime windowStartedAt,
               @Param("windowSeconds") int windowSeconds, @Param("now") LocalDateTime now);

    @Update("UPDATE rate_limit_bucket SET request_count=request_count+1,updated_at=#{now} WHERE bucket_key=#{bucketKey} " +
            "AND action_type=#{actionType} AND window_started_at=#{windowStartedAt} AND window_seconds=#{windowSeconds}")
    int increment(@Param("bucketKey") String bucketKey, @Param("actionType") String actionType,
                  @Param("windowStartedAt") LocalDateTime windowStartedAt,
                  @Param("windowSeconds") int windowSeconds, @Param("now") LocalDateTime now);

    @Select("SELECT MAX(TIMESTAMPADD(SECOND,window_seconds,window_started_at)) FROM rate_limit_bucket " +
            "WHERE bucket_key=#{bucketKey} AND action_type=#{actionType} AND request_count>=#{limit} " +
            "AND TIMESTAMPADD(SECOND,window_seconds,window_started_at)>#{now}")
    LocalDateTime nextAllowedAt(@Param("bucketKey") String bucketKey, @Param("actionType") String actionType,
                                @Param("limit") int limit, @Param("now") LocalDateTime now);
}
