package com.spzx.product.service.impl;

import com.spzx.common.core.utils.StringUtils;
import com.spzx.common.core.utils.uuid.UUID;
import com.spzx.product.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {

    @Autowired
    StringRedisTemplate stringRedisTemplate; //SpringBoot自动化配置

    /**
     * 5000个请求并发调用当前方法，期望num值应该是 5000，但是值不是5000。所以高并发下数据出现了问题。
     *      问题原因：++不是原子性的。
     * 1.解决办法：采用本地锁来保证原子性。synchronized或Lock
     *
     * 在集群环境下，本地锁就失效了。
     *
     * 解决办法：
     *      使用分布式锁解决问题。
     *          1.数据库实现分布式锁   了解
     *          2.redis实现分布式锁  setnx k v    重点
     *          3.zookeeper实现分布式锁  了解
     *
     *  redis实现分布式锁 - 初版
     *      问题1：加锁后服务器宕机，可能出现死锁问题。
     *          解决：增加锁的过期时间，如果线程不去释放锁，redis根据过期时间自动释放锁。
     *
     *      问题2：可能会释放其他服务器的锁。
     *          释放锁的时候，需要判断锁的值，是否为自己加锁时设置的值。是才会释放。否则不用释放了(自动过期释放过了)。
     *             String lockValue = stringRedisTemplate.opsForValue().get("lock");
     *             if(uuid.equals(lockValue)){ //锁是我加的。
     *                 stringRedisTemplate.delete("lock");
     *             }
     *
     *             这样操作依然存在问题：判断和删除锁不是原子的。依然可能出现删除别人的锁。
     *             使用Lua脚本保证原子性。
     *             另外，业务逻辑时间大于锁过期时间，业务逻辑在无锁的情况下执行，依然出现并发问题。
     *                  这个解决办法：采用锁续期解决。后期听书项目中使用Redisson框架实现。
     *
     *
     *
     */
    @Override
    public void testLock() {

        //Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent("lock", "lock");
        //stringRedisTemplate.expire("lock",60, TimeUnit.SECONDS); //设置值和设置过期时间两行代码不是原子的，依然可能出现死锁问题。

        String uuid = UUID.randomUUID().toString().replaceAll("-","");

        //四个参数的方法，设置值和设置时间就是原子性的。可以解决死锁问题。
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent("lock", uuid,3,TimeUnit.SECONDS);

        if(ifAbsent){ //加锁成功
            // 查询Redis中的num值
            String value = (String) this.stringRedisTemplate.opsForValue().get("num");
            // 没有该值return
            if (StringUtils.isBlank(value)) {
                return;
            }
            // 有值就转成成int
            int num = Integer.parseInt(value);
            // 把Redis中的num值+1
            this.stringRedisTemplate.opsForValue().set("num", String.valueOf(++num)); //  java 中  ++ 操作不是原子的。

            //解锁：避免释放其他人的锁。
            /*String lockValue = stringRedisTemplate.opsForValue().get("lock");
            if(uuid.equals(lockValue)){ //锁是我加的。
                stringRedisTemplate.delete("lock");
            }*/
            // LUA脚本保证原子性
            String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1]\n" +
                    "then\n" +
                    "    return redis.call(\"del\",KEYS[1])\n" +
                    "else\n" +
                    "    return 0\n" +
                    "end";
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(script);
            redisScript.setResultType(Long.class);

            Long result = stringRedisTemplate.execute(redisScript, Arrays.asList("lock"), uuid);
            if(result==1){
                System.out.println("解锁成功result = 1");
            }else{
                System.out.println("解锁失败,不能释放别人的锁。");
            }

        }else{ //加锁失败
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.testLock();  //自旋，休息一会再重新获取锁。
        }
    }

















    /*@Override
    public void testLock() {

        //Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent("lock", "lock");
        //stringRedisTemplate.expire("lock",60, TimeUnit.SECONDS); //设置值和设置过期时间两行代码不是原子的，依然可能出现死锁问题。

        //四个参数的方法，设置值和设置时间就是原子性的。可以解决死锁问题。
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent("lock", "lock",60,TimeUnit.SECONDS);

        if(ifAbsent){ //加锁成功
            // 查询Redis中的num值
            String value = (String) this.stringRedisTemplate.opsForValue().get("num");
            // 没有该值return
            if (StringUtils.isBlank(value)) {
                return;
            }
            // 有值就转成成int
            int num = Integer.parseInt(value);
            // 把Redis中的num值+1
            this.stringRedisTemplate.opsForValue().set("num", String.valueOf(++num)); //  java 中  ++ 操作不是原子的。

            //解锁
            stringRedisTemplate.delete("lock");

        }else{ //加锁失败
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.testLock();  //自旋，休息一会再重新获取锁。
        }
    }
*/












/*    @Override
    public synchronized void testLock() {

        // 查询Redis中的num值
        String value = (String) this.stringRedisTemplate.opsForValue().get("num");
        // 没有该值return
        if (StringUtils.isBlank(value)) {
            return;
        }
        // 有值就转成成int
        int num = Integer.parseInt(value);
        // 把Redis中的num值+1
        this.stringRedisTemplate.opsForValue().set("num", String.valueOf(++num)); //  java 中  ++ 操作不是原子的。

    }*/

}
