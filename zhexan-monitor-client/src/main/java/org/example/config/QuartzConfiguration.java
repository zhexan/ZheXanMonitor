package org.example.config;

import ch.qos.logback.core.CoreConstants;
import lombok.extern.slf4j.Slf4j;
import org.example.task.MonitorJobBean;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
@Configuration
public class QuartzConfiguration {

    @Bean
    public JobDetail jobDetailFactoryBean() {
        return JobBuilder.newJob(MonitorJobBean.class)
                .withIdentity("monitor-task")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger cronTriggerFactoryBean(JobDetail detail) {
        CronScheduleBuilder cron =  CronScheduleBuilder.cronSchedule("*/10 * * * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(detail)
                .withIdentity("monitor-trigger")
                .withSchedule(cron)
                .build();
    }
}
