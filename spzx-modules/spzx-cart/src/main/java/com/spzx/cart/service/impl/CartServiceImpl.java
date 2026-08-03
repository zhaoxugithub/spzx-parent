package com.spzx.cart.service.impl;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuPrice;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl implements ICartService {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    RemoteProductService remoteProductService;

    /**
     * 添加购物车
     *
     * @param skuId
     * @param skuNum
     */
    @Override
    public void addToCart(Long skuId, Integer skuNum) {
        //准备Redis Hash类型大key
        Long userId = SecurityContextHolder.getUserId(); //从当前线程上获取共享数据。HeaderInterceptor拦截器获取请求头信息，将数据绑定到当前线程上。
        String cartKey = getCartKey(userId);

        //准备Redis Hash类型小Key
        String hashKey = String.valueOf(skuId);

        //从Redis Hash类型中获取操作购物车的k-v map
        BoundHashOperations<String, String, CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);
        int threshold = 99; //单个商品数量的最大限额
        if (boundHashOperations.hasKey(hashKey)) { //以前添加过这个商品，修改购物车里这个商品的数量
            CartInfo cartInfo = boundHashOperations.get(hashKey);
            int newSkuNum = cartInfo.getSkuNum() + skuNum;
            cartInfo.setSkuNum(newSkuNum > threshold ? threshold : newSkuNum);
            cartInfo.setUpdateTime(new Date());
            boundHashOperations.put(hashKey, cartInfo);
        } else { //首次添加这个商品到购物车
            //购物车商品，每个人最多不能超过50个商品
            Long size = boundHashOperations.size();
            if (++size > 50) {
                throw new ServiceException("超出购物车最大商品数量50个!!!");
            }

            CartInfo cartInfo = new CartInfo();
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);

            //根据skuId调用spzx-product微服务接口，获取ProductSku数据
            R<ProductSku> productSkuResult = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            if (productSkuResult.getCode() == R.FAIL) {
                throw new ServiceException(productSkuResult.getMsg());
            }
            ProductSku productSku = productSkuResult.getData();
            cartInfo.setCartPrice(productSku.getSalePrice());
            cartInfo.setSkuPrice(productSku.getSalePrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setThumbImg(productSku.getThumbImg());
            cartInfo.setSkuName(productSku.getSkuName());
            cartInfo.setIsChecked(1);
            cartInfo.setCreateTime(new Date());

            boundHashOperations.put(hashKey, cartInfo);
        }
    }

    //生成购物车的key
    private String getCartKey(Long userId) {
        String cartKey = "user:cart:" + userId;
        return cartKey;
    }

    /**
     * 查看购物车列表数据
     * @return 列表数据
     */
    @Override
    public List<CartInfo> cartList() {
        //准备Redis Hash类型大key
        Long userId = SecurityContextHolder.getUserId(); //从当前线程上获取共享数据。HeaderInterceptor拦截器获取请求头信息，将数据绑定到当前线程上。
        String cartKey = getCartKey(userId);

        BoundHashOperations<String,String,CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartInfoList = boundHashOperations.values();
        if(CollectionUtils.isEmpty(cartInfoList)){
            return new ArrayList<>();
        }

        //购物车列表排序，根据添加时间进行倒序排序
        cartInfoList = cartInfoList.stream().sorted((o1, o2)-> o2.getCreateTime().compareTo(o1.getCreateTime())).toList();


        //远程获取商品最新价格，给与页面价格变动提示。
        List<Long> skuIdList = cartInfoList.stream().map(CartInfo::getSkuId).toList();
        R<List<SkuPrice>> skuPriceListResult = remoteProductService.getSkuPriceList(skuIdList, SecurityConstants.INNER);
        if(skuPriceListResult.getCode() == R.FAIL){
            throw new ServiceException(skuPriceListResult.getMsg());
        }
        List<SkuPrice> skuPriceList = skuPriceListResult.getData();//最新价格
        //List<SkuPrice> 转 Map<SkuId,skuPrice>
        Map<Long, BigDecimal> skuIdToSalePriceMap = skuPriceList.stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));


        //更新商品最新价格：更新内存中商品最新价格，而不是购物车中价格。因为没有重新放回到购物车中。
        for (CartInfo cartInfo : cartInfoList) {
            cartInfo.setSkuPrice(skuIdToSalePriceMap.get(cartInfo.getSkuId()));
        }

        return cartInfoList;
    }


    /**
     * 删除购物车商品
     * @param skuId
     */
    @Override
    public void deleteCart(Long skuId) {
        //准备Redis Hash类型大key
        Long userId = SecurityContextHolder.getUserId(); //从当前线程上获取共享数据。HeaderInterceptor拦截器获取请求头信息，将数据绑定到当前线程上。
        String cartKey = getCartKey(userId);

        //准备Redis Hash类型小Key
        String hashKey = String.valueOf(skuId);

        BoundHashOperations<String,String,CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);
        if(boundHashOperations.hasKey(hashKey)){
            boundHashOperations.delete(hashKey);
        }
    }

    /**
     * 修改选中状态
     * @param skuId
     * @param isChecked
     */
    @Override
    public void checkCart(Long skuId, Integer isChecked) {
        //准备Redis Hash类型大key
        Long userId = SecurityContextHolder.getUserId(); //从当前线程上获取共享数据。HeaderInterceptor拦截器获取请求头信息，将数据绑定到当前线程上。
        String cartKey = getCartKey(userId);

        //准备Redis Hash类型小Key
        String hashKey = String.valueOf(skuId);

        BoundHashOperations<String,String,CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);
        if(boundHashOperations.hasKey(hashKey)){
            CartInfo cartInfo = boundHashOperations.get(hashKey);
            cartInfo.setIsChecked(isChecked);
            boundHashOperations.put(hashKey,cartInfo);
        }
    }

    @Override
    public void allCheckCart(Integer isChecked) {
        //准备Redis Hash类型大key
        Long userId = SecurityContextHolder.getUserId(); //从当前线程上获取共享数据。HeaderInterceptor拦截器获取请求头信息，将数据绑定到当前线程上。
        String cartKey = getCartKey(userId);
        
        BoundHashOperations<String,String,CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);

        List<CartInfo> cartInfoList = boundHashOperations.values();
        if(!CollectionUtils.isEmpty(cartInfoList)){
            for (CartInfo cartInfo : cartInfoList) {
                cartInfo.setIsChecked(isChecked); //每一个商品选中状态都与总的状态一致
                boundHashOperations.put(cartInfo.getSkuId().toString(),cartInfo);
            }
        }
    }

    @Override
    public void clearCart() {
        //准备Redis Hash类型大key
        Long userId = SecurityContextHolder.getUserId(); //从当前线程上获取共享数据。HeaderInterceptor拦截器获取请求头信息，将数据绑定到当前线程上。
        String cartKey = getCartKey(userId);

        if(redisTemplate.hasKey(cartKey)){
            redisTemplate.delete(cartKey);
        }

    }


    @Override
    public List<CartInfo> getCartCheckedList(Long userId) {
        String cartKey = getCartKey(userId);
        BoundHashOperations<String,String,CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartInfoList = boundHashOperations.values();

        if(!CollectionUtils.isEmpty(cartInfoList)){
            return cartInfoList.stream().filter(cartInfo -> cartInfo.getIsChecked()==1).toList();
        }
        return new ArrayList<>();
    }


    @Override
    public Boolean updateCartPrice(Long userId) {
        //获取购物车所有商品。只更新下订单中商品在购物车中的价格。
        String cartKey = getCartKey(userId);
        BoundHashOperations<String,String,CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartInfoList = boundHashOperations.values();

        if(!CollectionUtils.isEmpty(cartInfoList)){
            List<CartInfo> checkedCartInfoList = cartInfoList.stream().filter(cartInfo -> cartInfo.getIsChecked() == 1).toList();
            if(!CollectionUtils.isEmpty(checkedCartInfoList)){
                List<Long> skuIdList = checkedCartInfoList.stream().map(cartInfo -> cartInfo.getSkuId()).toList();
                R<List<SkuPrice>> skuPriceListResult = remoteProductService.getSkuPriceList(skuIdList, SecurityConstants.INNER);
                if(skuPriceListResult.getCode()  == R.FAIL){
                    throw new ServiceException(skuPriceListResult.getMsg());
                }
                List<SkuPrice> skuPriceList = skuPriceListResult.getData();
                Map<Long, BigDecimal> skuIdToSalePriceMap = skuPriceList.stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));
                for (CartInfo checkedCartInfo : checkedCartInfoList) {
                    BigDecimal newPrice = skuIdToSalePriceMap.get(checkedCartInfo.getSkuId());
                    checkedCartInfo.setSkuPrice(newPrice);
                    checkedCartInfo.setCartPrice(newPrice);
                    boundHashOperations.put(checkedCartInfo.getSkuId().toString(),checkedCartInfo);
                }
                return true;
            }
        }

        return false;
    }


    @Override
    public Boolean deleteCartCheckedList(Long userId) {
        //获取购物车所有商品。只更新下订单中商品在购物车中的价格。
        String cartKey = getCartKey(userId);
        BoundHashOperations<String,String,CartInfo> boundHashOperations = redisTemplate.boundHashOps(cartKey);
        List<CartInfo> cartInfoList = boundHashOperations.values();
        if(!CollectionUtils.isEmpty(cartInfoList)){
            for (CartInfo cartInfo : cartInfoList) {
                if(cartInfo.getIsChecked() == 1){
                    boundHashOperations.delete(cartInfo.getSkuId().toString());
                }
            }
            return true;
        }
        return false;
    }
}