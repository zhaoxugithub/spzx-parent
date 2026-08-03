package com.spzx.cart.service;

import com.spzx.cart.api.domain.CartInfo;

import java.util.List;

public interface ICartService {

    /**
     * 添加购物车
     * @param skuId
     * @param skuNum
     */
    void addToCart(Long skuId, Integer skuNum);

    /**
     * 查看购物车列表
     * @return 购物车商品数据
     */
    List<CartInfo> cartList();

    /**
     * 删除购物车商品
     * @param skuId
     */
    void deleteCart(Long skuId);

    /**
     * 修改选中状态
     * @param skuId
     * @param isChecked
     */
    void checkCart(Long skuId, Integer isChecked);

    /**
     * 更新购物车商品全部选中状态
     * @param isChecked
     */
    void allCheckCart(Integer isChecked);

    /**
     * 清空购物车
     */
    void clearCart();

    /**
     * 获取购物车中打钩的商品
     * @param userId 登录用户id
     * @return 打钩商品列表
     */
    List<CartInfo> getCartCheckedList(Long userId);

    /**
     * 更新用户购物车列表价格
     * @param userId
     * @return
     */
    Boolean updateCartPrice(Long userId);

    /**
     * 删除用户购物车中选择的商品
     * @param userId
     * @return
     */
    Boolean deleteCartCheckedList(Long userId);
}
