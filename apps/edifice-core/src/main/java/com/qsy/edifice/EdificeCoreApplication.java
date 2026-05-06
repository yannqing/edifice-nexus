package com.qsy.edifice;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({
        "com.qsy.edifice.mapper"  // 业务服务 Mapper
})
public class EdificeCoreApplication {

    public static void main(String[] args) {
        // 加载 .env 文件中的环境变量
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()  // 如果 .env 文件不存在，不抛出异常
                .load();

        // 将 .env 中的变量设置为系统属性，以便 Spring Boot 能够读取
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        SpringApplication.run(EdificeCoreApplication.class, args);
    }

}
