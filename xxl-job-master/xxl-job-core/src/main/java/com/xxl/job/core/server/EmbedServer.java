package com.xxl.job.core.server;

import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.impl.ExecutorBizImpl;
import com.xxl.job.core.biz.model.*;
import com.xxl.job.core.thread.ExecutorRegistryThread;
import com.xxl.job.core.util.GsonTool;
import com.xxl.job.core.util.ThrowableUtil;
import com.xxl.job.core.util.XxlJobRemotingUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Copy from : https://github.com/xuxueli/xxl-rpc
 *  注册 服务
 *  将我们的项目 注册  到  web里面
 *  任务调度的 RPC 服务
 * @author jing
 */
public class EmbedServer {

//    创建日志对象
    private static final Logger logger = LoggerFactory.getLogger(EmbedServer.class);
//  将我们的项目注册到   web里面
    private ExecutorBiz executorBiz;
//  定义线程
    private Thread thread;


    /**
     *  根据  服务器地址  端口，项目名称，令牌
     *  开始
     *  端口 是当前项目 端口
     *  address  是 当前项目的地址
     */

    public void start(final String address, final int port, final String appname, final String accessToken) {
//        服务端调用客户端的接口定义  的实现类
        executorBiz = new ExecutorBizImpl();



        thread = new Thread(new Runnable() {
            @Override
            public void run() {
//                NioEventLoopGroup是处理netty连接和IO操作的NioEventLoop执行者组
//
//                对于服务端，netty会创建两个不同类型的组，一个成为bossGroup负责处理accept事件，
//                一个为workerGroup负责处理read/write/connect事件。
                // param
                EventLoopGroup bossGroup = new NioEventLoopGroup();
                EventLoopGroup workerGroup = new NioEventLoopGroup();

//                创建线程池
                ThreadPoolExecutor bizThreadPool = new ThreadPoolExecutor(
                        0,
                        200,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(2000),
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable r) {
                                return new Thread(r, "xxl-job, EmbedServer bizThreadPool-" + r.hashCode());
                            }
                        },
                        new RejectedExecutionHandler() {
                            @Override
                            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                                throw new RuntimeException("xxl-job, EmbedServer bizThreadPool is EXHAUSTED!");
                            }
                        });



                try {


                    // 启动netty服务器
                    // 主要是将我们配置文件写的端口启动一个Netty进程，
                    // 然后监听这个端口号，读取调度中心的调度数据，
                    // 做相应的任务执行
                    ServerBootstrap bootstrap = new ServerBootstrap();
                    bootstrap.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel channel) throws Exception {
                                    channel.pipeline()
                                            //  做长连接
                                            .addLast(new IdleStateHandler(0, 0, 30 * 3, TimeUnit.SECONDS))  // beat 3N, close if idle
                                            .addLast(new HttpServerCodec()) //  告诉Netty已Http协议解码数据流
                                            .addLast(new HttpObjectAggregator(5 * 1024 * 1024))  // merge request & reponse to FULL
                                            .addLast(new EmbedHttpServerHandler(executorBiz, accessToken, bizThreadPool));
                                }
                            })
                            .childOption(ChannelOption.SO_KEEPALIVE, true);

                    // bind  异步绑定
                    ChannelFuture future = bootstrap.bind(port).sync();

                    logger.info(">>>>>>>>>>> xxl-job remoting server start success, nettype = {}, port = {}", EmbedServer.class, port);

                    //   start registry  开始注册
                    //   address 是当前项目的  地址
                    startRegistry(appname, address);

                    // wait util stop
                    future.channel().closeFuture().sync();

                } catch (InterruptedException e) {
                    logger.info(">>>>>>>>>>> xxl-job remoting server stop.");
                } catch (Exception e) {
                    logger.error(">>>>>>>>>>> xxl-job remoting server error.", e);
                } finally {
                    // stop
                    try {
                        workerGroup.shutdownGracefully();
                        bossGroup.shutdownGracefully();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
        thread.setDaemon(true);    // daemon, service jvm, user thread leave >>> daemon leave >>> jvm leave
        thread.start();
    }


    /**
     *  停止  服务线程
     */
    public void stop() throws Exception {
        // destroy server thread
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        // stop registry
        stopRegistry();
        logger.info(">>>>>>>>>>> xxl-job remoting server destroy success.");
    }


    // ---------------------- registry ----------------------

    /**
     * netty_http
     * <p>
     * Copy from : https://github.com/xuxueli/xxl-rpc
     *  内部类
     *  通过 EmbedHttpServerHandler 来处理调度中心调度执行器中的任务
     *  new EmbedHttpServerHandler(executorBiz, accessToken, bizThreadPool)
     * @author xuxueli 2015-11-24 22:25:15
     */
    public static class EmbedHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

//        创建日志对象
        private static final Logger logger = LoggerFactory.getLogger(EmbedHttpServerHandler.class);

//        定义  我们项目  调用 web的  接口
        private ExecutorBiz executorBiz;
//        定义  令牌
        private String accessToken;
//        定义  线程池  项目初始化的时候，就创建了这个
        private ThreadPoolExecutor bizThreadPool;

//        构造函数
        public EmbedHttpServerHandler(ExecutorBiz executorBiz, String accessToken, ThreadPoolExecutor bizThreadPool) {
            this.executorBiz = executorBiz;
            this.accessToken = accessToken;
            this.bizThreadPool = bizThreadPool;
        }


//        当调度中心发起请求的时候，核心处理逻辑都在这个channelRead0方法里面。
        /**
         * 当调度中心发起请求的时候，
         * 核心处理逻辑都在这个channelRead0方法里面。
         */
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            // request parse
            //final byte[] requestBytes = ByteBufUtil.getBytes(msg.content());    // byteBuf.toString(io.netty.util.CharsetUtil.UTF_8);
//            请求数据  ,web  服务端 传过来的数据
            String requestData = msg.content().toString(CharsetUtil.UTF_8);
//            获取请求url   ,web  服务端 传过来的地址
            String uri = msg.uri();
//            请求方法  ,web  服务端 传过来的 请求的方法名称
            HttpMethod httpMethod = msg.method();
//            判断链接是不是  活跃的
            boolean keepAlive = HttpUtil.isKeepAlive(msg);
//            获取请求的  令牌
            String accessTokenReq = msg.headers().get(XxlJobRemotingUtil.XXL_JOB_ACCESS_TOKEN);
            // invoke   线程池执行
            // bizThreadPool  项目初始化的时候，就创建了这个
            bizThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    // do invoke  调用远程地址进行执行，获取返回结果
                    Object responseObj = process(httpMethod, uri, requestData, accessTokenReq);

                    // to json  格式化  返回 结果
                    String responseJson = GsonTool.toJson(responseObj);

                    // write response
                    writeResponse(ctx, keepAlive, responseJson);
                }
            });
        }



        /**
         * 根据  请求方法，url，请求数据，令牌  调用executorBiz 里面的方法
         * ; 这段代码就是根据不同的Server端不同的类型做相应的处理，
         * 有心跳、空闲心跳、执行任务、kill任务、写日志等类别
         */
        private Object process(HttpMethod httpMethod, String uri, String requestData, String accessTokenReq) {
            // valid   判断 web端  传过来的 方法类型
            if (HttpMethod.POST != httpMethod) {
                return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, HttpMethod not support.");
            }
            // valid   判断 web端  传过来的  路由
            if (uri == null || uri.trim().length() == 0) {
                return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping empty.");
            }
//            判断令牌
            if (accessToken != null
                    && accessToken.trim().length() > 0
                    && !accessToken.equals(accessTokenReq)) {
                return new ReturnT<String>(ReturnT.FAIL_CODE, "The access token is wrong.");
            }

            // services mapping
            try {
//                服务开始调用   就是web端  传过来的路径
                switch (uri) {
                    case "/beat":   // web项目验证我们自己的项目是不是启动 的，心跳检查，我们的项目可以走到这里
//                        说明我们的项目 启动的，就返回success ，就告诉web，我们 项目启动的了
                        return executorBiz.beat();
                    case "/idleBeat":  //空闲  心跳  检查
//                        IdleBeatParam 类里面只有任务id
                        IdleBeatParam idleBeatParam = GsonTool.fromJson(requestData, IdleBeatParam.class);
                        return executorBiz.idleBeat(idleBeatParam);
                    case "/run":
//                        web 端传过来 run  运行任务
                        TriggerParam triggerParam = GsonTool.fromJson(requestData, TriggerParam.class);
                        return executorBiz.run(triggerParam);
                    case "/kill":  // 停止任务
                        KillParam killParam = GsonTool.fromJson(requestData, KillParam.class);
                        return executorBiz.kill(killParam);
                    case "/log": // 查询日志
                        LogParam logParam = GsonTool.fromJson(requestData, LogParam.class);
                        return executorBiz.log(logParam);
                    default:
                        return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping(" + uri + ") not found.");
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return new ReturnT<String>(ReturnT.FAIL_CODE, "request error:" + ThrowableUtil.toString(e));
            }
        }



        /**
         * 将格式化返回  结果 写到ChannelHandlerContext里面
         */
        private void writeResponse(ChannelHandlerContext ctx, boolean keepAlive, String responseJson) {
            // write response
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(responseJson, CharsetUtil.UTF_8));   //  Unpooled.wrappedBuffer(responseJson)
//            设置内容类型
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");       // HttpHeaderValues.TEXT_PLAIN.toString()
//            设置 内容长度
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

//            刷新数据
            ctx.writeAndFlush(response);
        }

        /**
         * 刷新
         */
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }
        /**
         * 关闭
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error(">>>>>>>>>>> xxl-job provider netty_http server caught exception", cause);
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                ctx.channel().close();      // beat 3N, close if idle
                logger.debug(">>>>>>>>>>> xxl-job provider netty_http server close an idle channel.");
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }
    }




    // ---------------------- registry ----------------------

//    开始注册   根据项目名称，项目地址进行注册到当前服务
    public void startRegistry(final String appname, final String address) {
        // start registry   根据注册线程  进行注册
        ExecutorRegistryThread.getInstance().start(appname, address);
    }


//    根据注册线程  停止注册
    public void stopRegistry() {
        // stop registry
        ExecutorRegistryThread.getInstance().toStop();
    }
}
