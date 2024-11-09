package com.spzx.channel.service.impl;

import com.alibaba.fastjson2.JSON;
import com.spzx.channel.domain.ItemVo;
import com.spzx.channel.service.IItemService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.Product;
import com.spzx.product.api.domain.ProductDetails;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.product.api.domain.vo.SkuStockVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
@Slf4j
public class ItemServiceImpl implements IItemService {

    @Autowired
    private RemoteProductService remoteProductService; //api接口：动态代理实现远程调用。

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    ThreadPoolExecutor threadPoolExecutor; //线程池

    @Override
    public ItemVo item(Long skuId) throws Exception {

        //--解决缓存穿透问题-----------------------------------------------------------
        String key = "sku:product:data";
        Boolean isExist = redisTemplate.opsForValue().getBit(key, skuId);
        if(!isExist){
            throw new ServiceException("数据不存在-bitmap");
        }
        //---------------------------------------------------------------------------
        ItemVo itemVo = new ItemVo();

        //任务1.获取sku信息
        CompletableFuture<ProductSku> productSkuCompletableFuture = CompletableFuture.supplyAsync(() -> { //没有参数，有返回结果
            R<ProductSku> productSkuResult = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            if (R.FAIL == productSkuResult.getCode()) { //远程返回结果状态码是500，那么表示降级处理了。
                throw new ServiceException(productSkuResult.getMsg()); //抛异常给controller，继续抛给统一异常处理类。
            }
            ProductSku productSku = productSkuResult.getData();
            itemVo.setProductSku(productSku);
            return productSku;
        },threadPoolExecutor);

        //任务2.获取商品信息
        CompletableFuture<Void> productCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> { //有输入参数，不需要返回结果
            R<Product> productResult = remoteProductService.getProduct(productSku.getProductId(), SecurityConstants.INNER);
            if (R.FAIL == productResult.getCode()) {
                throw new ServiceException(productResult.getMsg());
            }
            Product product = productResult.getData();
            itemVo.setProduct(product);
            itemVo.setSliderUrlList(Arrays.asList(product.getSliderUrls().split(",")));
            //[{"key":"大小","valueList":["大款式","中款式","迷你款"]},{"key":"颜色","valueList":["白色","黑色"]}]
            itemVo.setSpecValueList(JSON.parseArray(product.getSpecValue()));
        }, threadPoolExecutor);


        //任务3.获取商品最新价格
        CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> { //不需要参数，没有返回结果
            R<SkuPrice> skuPriceResult = remoteProductService.getSkuPrice(skuId, SecurityConstants.INNER);
            if (R.FAIL == skuPriceResult.getCode()) {
                throw new ServiceException(skuPriceResult.getMsg());
            }
            SkuPrice skuPrice = skuPriceResult.getData();
            itemVo.setSkuPrice(skuPrice);
        }, threadPoolExecutor);


        //任务4.获取商品详情
        CompletableFuture<Void> productDetailsCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> {
            R<ProductDetails> productDetailsResult = remoteProductService.getProductDetails(productSku.getProductId(), SecurityConstants.INNER);
            if (R.FAIL == productDetailsResult.getCode()) {
                throw new ServiceException(productDetailsResult.getMsg());
            }
            ProductDetails productDetails = productDetailsResult.getData();
            itemVo.setDetailsImageUrlList(Arrays.asList(productDetails.getImageUrls().split(",")));
        }, threadPoolExecutor);


        //任务5.获取商品规格对应商品skuId信息
        CompletableFuture<Void> skuSpecValueMapCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> {
            R<Map<String, Long>> skuSpecValueResult = remoteProductService.getSkuSpecValue(productSku.getProductId(), SecurityConstants.INNER);
            if (R.FAIL == skuSpecValueResult.getCode()) {
                throw new ServiceException(skuSpecValueResult.getMsg());
            }
            Map<String, Long> skuSpecValueMap = skuSpecValueResult.getData();
            itemVo.setSkuSpecValueMap(skuSpecValueMap);
        }, threadPoolExecutor);


        //任务6.获取商品库存信息
        CompletableFuture<Void> skuStockVoCompletableFuture = productSkuCompletableFuture.thenAcceptAsync((productSku) -> {
            R<SkuStockVo> skuStockResult = remoteProductService.getSkuStock(skuId, SecurityConstants.INNER);
            if (R.FAIL == skuStockResult.getCode()) {
                throw new ServiceException(skuStockResult.getMsg());
            }
            SkuStockVo skuStockVo = skuStockResult.getData();
            itemVo.setSkuStockVo(skuStockVo);
            productSku.setStockNum(skuStockVo.getAvailableNum()); //productSku增加一个扩展字段。
        }, threadPoolExecutor);

        //阻塞主线程，6个异步全部完成，继续执行主线程任务。
        CompletableFuture.allOf(
                productSkuCompletableFuture,
                productCompletableFuture,
                skuPriceCompletableFuture,
                productDetailsCompletableFuture,
                skuSpecValueMapCompletableFuture,
                skuStockVoCompletableFuture
        ).join();

        return itemVo;
    }
}