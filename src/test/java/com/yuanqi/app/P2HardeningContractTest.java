package com.yuanqi.app;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuanqi.app.auth.security.SecurityConfig;
import com.yuanqi.app.photo.entity.PhotoTagRelation;
import com.yuanqi.app.photo.entity.RevisionMedia;
import com.yuanqi.app.photo.mapper.PhotoTagRelationMapper;
import com.yuanqi.app.photo.mapper.RevisionMediaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class P2HardeningContractTest {
    @Test
    void 复合键Mapper不继承ByIdAPI且实体不伪造单主键() {
        assertExplicitCompositeMapper(PhotoTagRelationMapper.class, PhotoTagRelation.class);
        assertExplicitCompositeMapper(RevisionMediaMapper.class, RevisionMedia.class);
    }

    @Test
    void 默认内存用户自动配置被显式关闭且自定义安全链仍存在() {
        SpringBootApplication application = AppApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(application.exclude()).contains(UserDetailsServiceAutoConfiguration.class);
        assertThat(Arrays.stream(SecurityConfig.class.getDeclaredMethods())
                .map(Method::getName)).contains("filterChain");
    }

    private void assertExplicitCompositeMapper(Class<?> mapper, Class<?> entity) {
        assertThat(BaseMapper.class.isAssignableFrom(mapper)).isFalse();
        assertThat(Arrays.stream(mapper.getMethods()).map(Method::getName))
                .doesNotContain("selectById", "updateById", "deleteById");
        assertThat(List.of(entity.getDeclaredFields())).noneMatch(field -> field.isAnnotationPresent(TableId.class));
    }
}
