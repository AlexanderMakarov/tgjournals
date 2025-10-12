package com.aleksandrmakarov.journals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {"telegram.bot.token=", "telegram.bot.username="})
class JournalsApplicationTests {

  @Test
  void contextLoads() {}
}
