package org.example.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

@Configuration
class JsonConfig {
    @Bean
    fun strictIntegerInput(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .withCoercionConfig(LogicalType.Integer) { config ->
                config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            }
            .withCoercionConfig(LogicalType.Boolean) { config ->
                config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            }
    }
}
