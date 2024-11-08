package com.spzx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.core.utils.bean.BeanUtils;
import com.spzx.product.api.domain.Product;
import com.spzx.product.api.domain.ProductDetails;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.product.api.domain.vo.SkuQuery;
import com.spzx.product.api.domain.vo.SkuStockVo;
import com.spzx.product.domain.SkuStock;
import com.spzx.product.mapper.ProductDetailsMapper;
import com.spzx.product.mapper.ProductMapper;
import com.spzx.product.mapper.ProductSkuMapper;
import com.spzx.product.mapper.SkuStockMapper;
import com.spzx.product.service.IProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品Service业务层处理
 */
@Slf4j
@Service
@Transactional
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private ProductDetailsMapper productDetailsMapper;

    @Autowired
    private SkuStockMapper skuStockMapper;


    //@Autowired
    //private StringRedisTemplate stringRedisTemplate; //适合 key和value都是字符串类型

    @Autowired
    private RedisTemplate redisTemplate; //适合值是任意类型

    /**
     * 查询商品列表
     *
     * @param product 商品
     * @return 商品
     */
    @Override
    public List<Product> selectProductList(Product product) {
        return productMapper.selectProductList(product);
    }

    //原子性
    @Override
    public int insertProduct(Product product) {
        //1.保存Product对象到product表
        productMapper.insert(product); //主键回填

        //2.保存List<ProductSku>对象到product_sku表
        List<ProductSku> productSkuList = product.getProductSkuList();
        if (CollectionUtils.isEmpty(productSkuList)) {
            throw new ServiceException("SKU数据为空");
        }
        int size = productSkuList.size();
        for (int i = 0; i < size; i++) {
            ProductSku productSku = productSkuList.get(i);
            productSku.setSkuCode(product.getId() + "_" + i);
            productSku.setSkuName(product.getName() + " " + productSku.getSkuSpec());
            productSku.setProductId(product.getId());
            productSkuMapper.insert(productSku);

            //添加商品库存  //3.保存List<SkuStock>对象到sku_stock表
            SkuStock skuStock = new SkuStock();
            skuStock.setSkuId(productSku.getId());
            skuStock.setTotalNum(productSku.getStockNum());
            skuStock.setLockNum(0);
            skuStock.setAvailableNum(productSku.getStockNum());
            skuStock.setSaleNum(0);
            skuStockMapper.insert(skuStock);
        }

        //4.保存ProductDetails对象到product_details表
        ProductDetails productDetails = new ProductDetails();
        productDetails.setImageUrls(String.join(",", product.getDetailsImageUrlList()));
        productDetails.setProductId(product.getId());
        productDetailsMapper.insert(productDetails);

        return 1;
    }


    @Override
    public Product selectProductById(Long id) {
        //1.根据id查询Product对象
        Product product = productMapper.selectById(id);

        //2.封装扩展字段：查询商品对应多个List<ProductSku>
        //select * from product_sku where product_id =?
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        List<Long> productSkuIdList = productSkuList.stream().map(productSku -> productSku.getId()).toList();


        // select * from sku_stock where sku_id in (1,2,3,4,5,6)
        List<SkuStock> skuStockList = skuStockMapper.selectList(new LambdaQueryWrapper<SkuStock>().in(SkuStock::getSkuId, productSkuIdList));

        Map<Long, Integer> skuIdToTatalNumMap = skuStockList.stream().collect(Collectors.toMap(SkuStock::getSkuId, SkuStock::getTotalNum));
        productSkuList.forEach(productSku -> {
            //返回ProductSku对象，携带了库存数据；
            productSku.setStockNum(skuIdToTatalNumMap.get(productSku.getId()));
        });

        product.setProductSkuList(productSkuList);

        //3.封装扩展字段：商品详情图片List<String>
        ProductDetails productDetails = productDetailsMapper.selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, id));
        String imageUrls = productDetails.getImageUrls();   //url,url,url
        String[] urls = imageUrls.split(",");
        product.setDetailsImageUrlList(Arrays.asList(urls));
        //返回Product对象
        return product;
    }


    @Override
    public int updateProduct(Product product) {

        //延时双删 保证 数据一致性    最终一致性。
        //修改数据库数据之前，先删除一遍缓存数据
        List<ProductSku> productSkuList = product.getProductSkuList();
        List<Long> skuIdList = productSkuList.stream().map(ProductSku::getId).toList();

        for (Long skuId : skuIdList) {
            String dataKey = "product:sku:" + skuId;
            redisTemplate.delete(dataKey);
        }


        //1.更新Product
        productMapper.updateById(product);

        //2.更新SKU   List<ProductSku>
        if (CollectionUtils.isEmpty(productSkuList)) {
            throw new ServiceException("SKU数据为空");
        }
        productSkuList.forEach(productSku -> {
            productSkuMapper.updateById(productSku);

            //3.更新库存   List<ProductSku> -> 获取扩展字段stockNum
            SkuStock skuStock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>().eq(SkuStock::getSkuId, productSku.getId()));
            skuStock.setTotalNum(productSku.getStockNum());
            skuStock.setAvailableNum(skuStock.getTotalNum() - skuStock.getLockNum());
            skuStockMapper.updateById(skuStock);
        });

        //4.更新详情ProductDetails
        ProductDetails productDetails = productDetailsMapper
                .selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, product.getId()));
        productDetails.setImageUrls(String.join(",", product.getDetailsImageUrlList()));
        productDetailsMapper.updateById(productDetails);


        //修改数据库后，睡一会再删除一遍缓存。
        try {
            Thread.sleep(500); //保证数据库端主从复制可以全部完成。
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (Long skuId : skuIdList) {
            String dataKey = "product:sku:" + skuId;
            redisTemplate.delete(dataKey);
        }

        return 1;
    }


    /*@Override
    public int updateProduct(Product product) {

        //1.更新Product
        productMapper.updateById(product);

        //2.更新SKU   List<ProductSku>
        List<ProductSku> productSkuList = product.getProductSkuList();
        if (CollectionUtils.isEmpty(productSkuList)) {
            throw new ServiceException("SKU数据为空");
        }
        productSkuList.forEach(productSku -> {
            productSkuMapper.updateById(productSku);

            //3.更新库存   List<ProductSku> -> 获取扩展字段stockNum
            SkuStock skuStock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>().eq(SkuStock::getSkuId, productSku.getId()));
            skuStock.setTotalNum(productSku.getStockNum());
            skuStock.setAvailableNum(skuStock.getTotalNum() - skuStock.getLockNum());
            skuStockMapper.updateById(skuStock);
        });

        //4.更新详情ProductDetails
        ProductDetails productDetails = productDetailsMapper
                .selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, product.getId()));
        productDetails.setImageUrls(String.join(",", product.getDetailsImageUrlList()));
        productDetailsMapper.updateById(productDetails);

        return 1;
    }*/


    @Override
    public int deleteProductByIds(Long[] ids) {
        //1.删除Product表数据
        // delete from product where id in (1,2)
        productMapper.deleteBatchIds(Arrays.asList(ids));

        //2.删除ProductSku表数据
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId, Arrays.asList(ids)));
        List<Long> productSkuIdList = productSkuList.stream().map(ProductSku::getId).toList();
        productSkuMapper.deleteBatchIds(productSkuIdList);

        //3.删除SkuStock表数据
        skuStockMapper.delete(new LambdaQueryWrapper<SkuStock>().in(SkuStock::getSkuId, productSkuIdList));

        //4.删除ProductDetails表数据
        // delete from product_details where product_id in (1,2)
        productDetailsMapper.delete(new LambdaQueryWrapper<ProductDetails>().in(ProductDetails::getProductId, Arrays.asList(ids)));
        return 1;
    }


    @Override
    public void updateAuditStatus(Long id, Integer auditStatus) {
        Product product = new Product();
        product.setId(id);
        if (auditStatus == 1) {
            product.setAuditStatus(1);
            product.setAuditMessage("审批通过");
        } else {
            product.setAuditStatus(-1);
            product.setAuditMessage("审批拒绝");
        }
        productMapper.updateById(product);
    }

    @Transactional
    @Override
    public void updateStatus(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        if (status == 1) {
            product.setStatus(1); //spu上架了，那么对应的多个sku也需要上架。
            ProductSku productSkuUpdate = new ProductSku();
            //productSkuUpdate.setProductId(id);
            productSkuUpdate.setStatus(1);
            productSkuMapper.update(productSkuUpdate,new LambdaUpdateWrapper<ProductSku>().eq(ProductSku::getProductId,id));

            //新上架的sku也需要存放到bitmap中
            List<ProductSku> skuList = productSkuMapper
                    .selectList(new LambdaQueryWrapper<ProductSku>()
                            .eq(ProductSku::getStatus, 1)
                            .eq(ProductSku::getProductId, id));
            if(!CollectionUtils.isEmpty(skuList)){
                String key = "sku:product:data";
                for (ProductSku productSku : skuList) {
                    Long skuId = productSku.getId();
                    redisTemplate.opsForValue().setBit(key,skuId,true); //  true表示1   false表示0
                }
            }

        } else {

            List<ProductSku> skuList = productSkuMapper
                    .selectList(new LambdaQueryWrapper<ProductSku>()
                            .eq(ProductSku::getStatus, 1)
                            .eq(ProductSku::getProductId, id));
            if(!CollectionUtils.isEmpty(skuList)){
                String key = "sku:product:data";
                for (ProductSku productSku : skuList) {
                    Long skuId = productSku.getId();
                    //redisTemplate.delete(key); //当前商品下架了，其他商品还没有下架。不能删除key
                    redisTemplate.opsForValue().setBit(key,skuId,false);
                    String dataKey = "product:sku:" + skuId;   //注意key名称一致。
                    redisTemplate.delete(dataKey);
                }
            }

            product.setStatus(-1);
            ProductSku productSkuUpdate = new ProductSku();
            //productSkuUpdate.setProductId(id);
            productSkuUpdate.setStatus(-1);
            productSkuMapper.update(productSkuUpdate,new LambdaUpdateWrapper<ProductSku>().eq(ProductSku::getProductId,id));

        }
        productMapper.updateById(product);
    }


    @Override
    public List<ProductSku> getTopSale() {
        return productSkuMapper.getTopSale();
    }


    @Override
    public List<ProductSku> skuList(SkuQuery skuQuery) {
        return productSkuMapper.skuList(skuQuery);
    }


    /**
     * 服务提供者：6个接口来服务于商品详情查询。需要进行优化，提供查询效率。
     * 需要使用redis来提高性能。
     * <p>
     * 先从缓存获取数据，缓存中存在直接返回，缓存中不存在获取分布式锁（避免缓存击穿），从数据库获取。然后，存放到缓存中，下次利用。
     * 这里注意哪些问题？
     * 查询null也要存放到缓存中(一定程度上缓解缓存穿透)，缓存时间比正常数据短一些。失效时间随机增加1-5分钟，避免缓存雪崩。
     * 增加bitmap或布隆过滤器，彻底解决缓存穿透的问题。
     */
    @Override
    public ProductSku getProductSku(Long skuId) {
        try {
            //1.先从缓存中获取，有则直接返回
            String dataKey = "product:sku:" + skuId;
            ProductSku productSku = (ProductSku) redisTemplate.opsForValue().get(dataKey);
            if (productSku != null) {
                log.info("从缓存中获取了productSku = " + productSku);
                return productSku;
            }
            //2.缓存没有获取分布式锁
            String lockKey = "product:sku:lock:" + skuId;
            String lockVal = UUID.randomUUID().toString().replaceAll("-", ""); //加锁的标记，续期、释放锁都需要根据这个标记进行操作。
            Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent(lockKey, lockVal, 5, TimeUnit.SECONDS);
            if (ifAbsent) {
                try {
                    //2.1获取锁成功从数据库获取，存放到缓存
                    productSku = getSkuFromDB(skuId);
                    int random = new Random().nextInt(5);
                    int expireTime = productSku == null ? 5 * 60 + random : 10 * 60 + random;
                    redisTemplate.opsForValue().set(dataKey, productSku, expireTime, TimeUnit.SECONDS);
                    return productSku;
                } finally {
                    //释放锁，保证原子性-lua    释放自己的锁，避免释放他人的锁。
                    String script = "if redis.call('get',KEYS[1]) == ARGV[1] then\n" +
                            "\treturn redis.call('del',KEYS[1])\n" +
                            "else\n" +
                            "\treturn 0\n" +
                            "end";
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<Long>();
                    redisScript.setScriptText(script);
                    redisScript.setResultType(Long.class);
                    Long result = (Long)redisTemplate.execute(redisScript, Arrays.asList(lockKey), lockVal);
                    log.info("释放锁是否成功 result = "+result);
                }
            } else {
                //2.2获取锁失败睡觉自旋
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return this.getProductSku(skuId); //自旋，调用当前方法，重试。
            }
        } catch (Exception e) {
            e.printStackTrace();
            return getSkuFromDB(skuId); //如果存在异常，兜底，从数据库获取数据。
        }
    }

    //从数据库获取
    private ProductSku getSkuFromDB(Long skuId) {
        ProductSku productSku = productSkuMapper.selectById(skuId);
        return productSku;
    }



/*    @Override
    public ProductSku getProductSku(Long skuId) {
        return productSkuMapper.selectById(skuId);
    }*/


    @Override
    public Product getProduct(Long id) {
        return productMapper.selectById(id);
    }


    @Override
    public SkuPrice getSkuPrice(Long skuId) {
        ProductSku productSku = productSkuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId).select(ProductSku::getSalePrice, ProductSku::getMarketPrice));
        SkuPrice skuPrice = new SkuPrice();
        BeanUtils.copyProperties(productSku, skuPrice);
        return skuPrice;
    }


    @Override
    public ProductDetails getProductDetails(Long id) {
        return productDetailsMapper.selectOne(new LambdaQueryWrapper<ProductDetails>().eq(ProductDetails::getProductId, id));
    }

    /**
     * "skuSpecValueMap": {
     * "黑色 + 18G": 6,
     * "红色 + 18G": 4,
     * "白色 + 8G": 1,
     * "白色 + 18G": 2,
     * "黑色 + 8G": 5,
     * "红色 + 8G": 3
     * }
     *
     * @param id productId
     * @return
     */
    @Override
    public Map<String, Long> getSkuSpecValue(Long id) {
        List<ProductSku> productSkuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id).select(ProductSku::getId, ProductSku::getSkuSpec));
        Map<String, Long> skuSpecValueMap = new HashMap<>();
        productSkuList.forEach(item -> {
            skuSpecValueMap.put(item.getSkuSpec(), item.getId());
        });
        return skuSpecValueMap;
    }


    @Override
    public SkuStockVo getSkuStock(Long skuId) {
        SkuStock skuStock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>().eq(SkuStock::getSkuId, skuId));
        SkuStockVo skuStockVo = new SkuStockVo();
        BeanUtils.copyProperties(skuStock, skuStockVo);
        return skuStockVo;
    }


    // select * from product_sku where id in (1,2,3)
    // select id,sale_price,market_price from product_sku where id in (1,2,3)
    @Override
    public List<SkuPrice> getSkuPriceList(List<Long> skuIdList) {
        if (CollectionUtils.isEmpty(skuIdList)) {
            return new ArrayList<SkuPrice>();
        }
        List<ProductSku> skuList = productSkuMapper
                .selectList(new LambdaQueryWrapper<ProductSku>().in(ProductSku::getId, skuIdList)
                        .select(ProductSku::getId, ProductSku::getSalePrice, ProductSku::getMarketPrice));
        if (CollectionUtils.isEmpty(skuList)) {
            return new ArrayList<SkuPrice>();
        }
        return skuList.stream().map((sku) -> {
            SkuPrice skuPrice = new SkuPrice();
            skuPrice.setSkuId(sku.getId());
            skuPrice.setSalePrice(sku.getSalePrice());
            skuPrice.setMarketPrice(sku.getMarketPrice());
            return skuPrice;
        }).toList();
    }

}