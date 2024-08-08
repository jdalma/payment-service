package com.example.paymentservice.common

import org.springframework.stereotype.Component

/**
 * 외부의 웹 요청을 받아서 어플리케이션으로 요청을 전달하는 역할을 담당하는 Web Adapter 라는 의미이다.
 */
@Target(AnnotationTarget.CLASS)
@Component
annotation class WebAdapter { }
