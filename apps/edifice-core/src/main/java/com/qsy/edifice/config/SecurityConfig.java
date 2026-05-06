package com.qsy.edifice.config;

import com.qsy.edifice.common.Constant;
import com.qsy.edifice.security.OaAwarePasswordEncoder;
import com.qsy.edifice.security.filter.JwtAuthenticationTokenFilter;
import com.qsy.edifice.security.handler.MyLoginFailureHandler;
import com.qsy.edifice.security.handler.MyLoginSuccessHandler;
import com.qsy.edifice.security.handler.MyLogoutSuccessHandler;
import com.qsy.edifice.utils.RedisCache;
import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Resource
    private RedisCache redisCache;

    @Resource
    private MyLoginSuccessHandler myLoginSuccessHandler;

    @Resource
    private MyLoginFailureHandler myLoginFailureHandler;

    @Resource
    private MyLogoutSuccessHandler myLogoutSuccessHandler;

    @Resource
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //允许一些请求匿名访问，其他的均需要认证
        http.authorizeHttpRequests((authorize)->authorize
                .requestMatchers(Constant.anonymousConstant)
                .permitAll()
                .requestMatchers(Constant.anonymousMatch)
                .permitAll()
                // 允许定时任务的请求
                .requestMatchers("/scheduled/**").permitAll()
                // 允许 Token 刷新接口匿名访问
                .requestMatchers("/auth/refresh", "/auth/verify").permitAll()
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .anyRequest()
                .authenticated()
        );

        //关闭session
        http.sessionManagement((sessionManagement)->
                sessionManagement
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    // 添加这行：支持异步请求
                    .enableSessionUrlRewriting(true)
        );

        //设置用户名密码认证前的jwt过滤器
        http.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);

        //csrf
        http.csrf(AbstractHttpConfigurer::disable);

        //登录可以选择form表单登录，也可选择发送请求，写到controller中
        //form表单登录
        http.formLogin((login)->login.
                loginProcessingUrl("/auth/login")
                .successHandler(myLoginSuccessHandler)
                .failureHandler(myLoginFailureHandler)
        );

        //设置退出logout过滤器
        http.logout((logout)->logout
                .logoutUrl("/auth/logout")
                .logoutSuccessHandler(myLogoutSuccessHandler)
        );

        //关闭跨域拦截--适用于前后端分离，另创建跨域拦截的类
        http.cors(Customizer.withDefaults());

        return http.build();
    }
    /**
     * 对密码进行BCrypt加密
     * @return 返回 BCryptEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                           OaUserSyncProperties oaUserSyncProperties){
        return new OaAwarePasswordEncoder(jdbcTemplate, oaUserSyncProperties);
    }


    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Access Denied: {}", accessDeniedException.getMessage());
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(403);
            response.getWriter().write("{\"message\": \"Access Denied\"}");
        };
    }
}
