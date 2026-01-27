package org.example.task;

import jakarta.annotation.Resource;
import lombok.Data;
import org.example.entity.RuntimeDetail;
import org.example.utils.MonitorUtils;
import org.example.utils.NetUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
public class MonitorJobBean extends QuartzJobBean {

    @Resource
    MonitorUtils monitor;
    @Resource
    NetUtils net;

    /**
     *
     * @param context
     * @throws JobExecutionException
     * @since 2026-1-24
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        RuntimeDetail runtimeDetail = monitor.monitorRuntimeDetail();
        net.updateRuntimeDetails(runtimeDetail);
    }
}
