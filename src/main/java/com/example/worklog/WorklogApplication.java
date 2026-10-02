package com.example.worklog;

import com.example.worklog.abstraction.LlmProperties;
import com.example.worklog.weekly.WorkFormatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({LlmProperties.class, WorkFormatProperties.class})
public class WorklogApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorklogApplication.class, args);
    }
}
