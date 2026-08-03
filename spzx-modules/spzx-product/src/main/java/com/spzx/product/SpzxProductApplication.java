package com.spzx.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spzx.common.security.annotation.EnableCustomConfig;
import com.spzx.common.security.annotation.EnableRyFeignClients;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.mapper.ProductSkuMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.List;


@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class SpzxProductApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(SpzxProductApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  系统模块启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }

    @Autowired
    ProductSkuMapper productSkuMapper;

    @Autowired
    RedisTemplate redisTemplate;

    //在服务器启动时进行初始化 bitmap 二进制bit数组，用于解决缓存穿透。
    @Override
    public void run(String... args) throws Exception {
        //1.查询所有上线的商品skuId,放在bitmap中
        List<ProductSku> skuList = productSkuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getStatus, 1));
        if(!CollectionUtils.isEmpty(skuList)){
            String key = "sku:product:data";
            for (ProductSku productSku : skuList) {
                Long skuId = productSku.getId();
                redisTemplate.opsForValue().setBit(key,skuId,true); //  true表示1   false表示0
            }
        }
    }
}
