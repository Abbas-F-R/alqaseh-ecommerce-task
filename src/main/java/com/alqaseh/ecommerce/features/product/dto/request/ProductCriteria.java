package com.alqaseh.ecommerce.features.product.dto.request;

import com.alqaseh.ecommerce.features.product.entity.ProductCategory;

/** The filters both product lists share (name contains, category); pagination is specific to each list. */
public interface ProductCriteria {

    String getName();

    ProductCategory getCategory();
}
