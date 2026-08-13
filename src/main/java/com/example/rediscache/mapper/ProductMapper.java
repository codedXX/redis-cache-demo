package com.example.rediscache.mapper;

import com.example.rediscache.pojo.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 商品数据访问接口，SQL 定义在 ProductMapper.xml 中。 */
@Mapper
public interface ProductMapper {

    Product selectById(@Param("id") Long id);
}
