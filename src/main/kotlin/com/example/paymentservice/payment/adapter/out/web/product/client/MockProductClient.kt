package com.example.paymentservice.payment.adapter.out.web.product.client

import com.example.paymentservice.payment.domain.Product
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class MockProductClient: ProductClient {

    override fun getProducts(cartId: Long, productIds: List<Long>): Flux<Product> {
        return Flux.fromIterable(
            productIds.map {
                Product(it, it * 10000, 2 , "test_product_$it", 1)
            }
        )
    }
}
