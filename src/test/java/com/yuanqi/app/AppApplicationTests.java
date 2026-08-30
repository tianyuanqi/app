package com.yuanqi.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AppApplicationTests {
	@Autowired ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void 不注册Spring默认内存用户服务() {
		assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
	}

}
