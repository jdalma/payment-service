package com.example.paymentservice.payment.adapter.out.web.product

import com.example.paymentservice.common.WebAdapter
import com.example.paymentservice.payment.adapter.out.web.product.client.ProductClient
import com.example.paymentservice.payment.application.port.out.LoadProductPort
import com.example.paymentservice.payment.domain.Product
import reactor.core.publisher.Flux

/**
 * 이 어댑터는 웹 기반 요청을 통해 상품 정보를 가지고 오기 때문에 web 패키지에 위치한다.
 * 실제 서비스가 마이크로 서비스로 구성되었다는 가정 하에 고려한 설계이다.
 */
@WebAdapter
class ProductWebAdapter (
    private val productClient: ProductClient
): LoadProductPort {

    override fun getProducts(cartId: Long, productIds: List<Long>): Flux<Product> {
        return productClient.getProducts(cartId, productIds)
    }
}
