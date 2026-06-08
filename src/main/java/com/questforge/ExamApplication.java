package com.questforge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 在线考试与成绩分析平台 - 后端主启动类
 */
@SpringBootApplication
@MapperScan("com.questforge.mapper") // 扫描并注册所有的 MyBatis Mapper 接口
public class ExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamApplication.class, args);

        System.out.println("==================================================");
        System.out.println("========   Exam Platform 服务启动成功!   =========");
        System.out.println("========   企业级高并发在线考试系统      =========");
        System.out.println("==================================================");
    }
}