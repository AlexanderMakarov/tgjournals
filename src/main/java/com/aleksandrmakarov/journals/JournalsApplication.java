package com.aleksandrmakarov.journals;

import com.aleksandrmakarov.journals.config.RepositoryRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints({RepositoryRuntimeHints.class})
public class JournalsApplication {

  public static void main(String[] args) {
    SpringApplication.run(JournalsApplication.class, args);
  }
}
