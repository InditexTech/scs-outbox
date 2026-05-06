package dev.inditex.scsoutbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.inditex.scsoutbox.OutboxMessageRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ExecutorServiceAutoConfigurationTest {

  @Nested
  @SpringBootTest(
      classes = {OutboxAutoConfiguration.class, RefreshAutoConfiguration.class},
      properties = {})
  class NoExecutorServiceDefined extends AbstractExecutorService {

    @Test
    void creates_one_by_default() {
      final Map<String, ExecutorService> beans = this.context.getBeansOfType(ExecutorService.class);
      System.out.println(beans);
      assertThat(this.executorService).isEqualTo(beans.get("defaultOutboxExecutorService"));
    }

  }

  @Nested
  @SpringBootTest(
      classes = {ExecutorServiceDefined.TestConfig.class, OutboxAutoConfiguration.class, RefreshAutoConfiguration.class},
      properties = {})
  class ExecutorServiceDefined extends AbstractExecutorService {

    @Test
    void uses_the_defined() {
      final Map<String, ExecutorService> beans = this.context.getBeansOfType(ExecutorService.class);
      System.out.println(beans);
      assertThat(this.executorService).isEqualTo(beans.get("executorService"));
    }

    @TestConfiguration
    public static class TestConfig {

      @Bean
      public ExecutorService executorService() {
        return Executors.newFixedThreadPool(1);
      }
    }
  }

  @Nested
  @SpringBootTest(
      classes = {OutboxExecutorServiceDefined.TestConfig.class, OutboxAutoConfiguration.class, RefreshAutoConfiguration.class},
      properties = {})
  class OutboxExecutorServiceDefined extends AbstractExecutorService {

    @Test
    void uses_outbox_executor_service() {
      final Map<String, ExecutorService> beans = this.context.getBeansOfType(ExecutorService.class);
      System.out.println(beans);
      assertThat(this.executorService).isEqualTo(beans.get("outboxExecutorService"));
    }

    @TestConfiguration
    public static class TestConfig {

      @Bean
      @Primary
      public ExecutorService primaryService() {
        return Executors.newFixedThreadPool(1);
      }

      @Bean
      public ExecutorService outboxExecutorService() {
        return Executors.newFixedThreadPool(1);
      }
    }
  }

  @Nested
  @SpringBootTest(
      classes = {PrimaryExecutorServiceDefined.TestConfig.class, OutboxAutoConfiguration.class, RefreshAutoConfiguration.class},
      properties = {})
  class PrimaryExecutorServiceDefined extends AbstractExecutorService {

    @Test
    void uses_the_primary() {
      final Map<String, ExecutorService> beans = this.context.getBeansOfType(ExecutorService.class);
      System.out.println(beans);
      assertThat(this.executorService).isEqualTo(beans.get("primaryExecutorService"));
    }

    @TestConfiguration
    public static class TestConfig {

      @Bean
      @Primary
      public ExecutorService primaryExecutorService() {
        return Executors.newFixedThreadPool(1);
      }

      @Bean
      public ExecutorService nonPrimaryExecutorService() {
        return Executors.newFixedThreadPool(1);
      }
    }
  }

  static class AbstractExecutorService {

    @Autowired
    protected ApplicationContext context;

    @Autowired
    @Qualifier("outboxExecutorService")
    protected ExecutorService executorService;

    @MockitoBean(name = "outboxMessageRepository")
    private OutboxMessageRepository outboxMessageRepository;

    @MockitoBean(name = "publishingOutboxMessageRepository")
    private OutboxMessageRepository publishingOutboxMessageRepository;

    @MockitoBean
    private BindingServiceProperties bindingServiceProperties;

    @MockitoBean
    private CompositeMessageConverter compositeMessageConverter;

    @MockitoBean
    private StreamBridge streamBridge;

  }

}
