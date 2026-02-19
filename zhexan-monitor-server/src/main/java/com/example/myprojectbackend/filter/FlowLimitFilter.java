package com.example.myprojectbackend.filter;

import com.example.myprojectbackend.entity.RestBean;
import com.example.myprojectbackend.utils.Const;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Const.ORDER_LIMIT)
public class FlowLimitFilter extends HttpFilter {
    @Resource
    StringRedisTemplate template;
    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String address = request.getRemoteAddr();
        if (this.tryCount(address)) {
            chain.doFilter(request, response);
        } else {
            this.writeBookMessage(response);
        }


    }
    private void writeBookMessage(HttpServletResponse response) throws IOException {
        log.info("访问被限制");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(RestBean.failure(400, "访问过于频繁，请稍后再试").asJSONString());
    }

    /**
     * 尝试对指定IP进行流量计数检查
     * 
     * @param ip 客户端IP地址
     * @return true表示允许访问，false表示被限流阻止
     */
    private boolean tryCount(String ip) {
        // 使用IP作为锁对象，确保同一IP的并发访问同步处理
        synchronized (ip.intern()) {
            // 检查该IP是否已被加入黑名单（被限流阻止）
            if(Boolean.TRUE.equals(template.hasKey(Const.FLOW_LIMIT_BLOCK + ip))) {
                return false;
            }
            // 执行周期性限流检查
            return limitPeriodCheck(ip);
        }
    }

    /**
     * 检查指定IP在限制周期内的访问次数
     * 
     * @param ip 客户端IP地址
     * @return true表示在限制范围内允许访问，false表示超过限制需要阻止
     */
    private boolean limitPeriodCheck(String ip) {
        // 检查该IP是否已有计数器记录
        if (Boolean.TRUE.equals(template.hasKey(Const.FLOW_LIMIT_COUNTER + ip))) {
            // 增加访问计数并获取当前值
            long increment = Optional.ofNullable(template.opsForValue().increment(Const.FLOW_LIMIT_COUNTER + ip)).orElse(0L);
            // 如果访问次数超过10次，则加入黑名单30秒
            if (increment > 10) {
                template.opsForValue().set(Const.FLOW_LIMIT_BLOCK + ip, "", 30, TimeUnit.SECONDS);
                return false;
            }
        } else {
            // 首次访问，初始化计数器为1，有效期3秒
            template.opsForValue().set(Const.FLOW_LIMIT_COUNTER + ip, "1", 3, TimeUnit.SECONDS);
        }
        return true;
    }
}
