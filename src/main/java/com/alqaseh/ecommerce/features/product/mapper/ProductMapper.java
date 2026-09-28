package com.alqaseh.ecommerce.features.product.mapper;

import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.dto.response.AdminProductResponse;
import com.alqaseh.ecommerce.features.product.dto.response.CustomerProductResponse;
import com.alqaseh.ecommerce.features.product.entity.Product;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Pure field mapping. Stock status is computed by the domain ({@link Product#getStockStatus()}), not here. */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    // Only the business fields come from the request; id, version and audit metadata are never client-controlled.
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name")
    @Mapping(target = "category")
    @Mapping(target = "price")
    @Mapping(target = "cost")
    @Mapping(target = "availableQuantity")
    Product toEntity(ProductRequest request);

    AdminProductResponse toAdminResponse(Product product);

    CustomerProductResponse toCustomerResponse(Product product);
}
