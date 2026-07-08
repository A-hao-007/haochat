package com.ahao.haochat.common.websocket;

import cn.hutool.core.net.url.UrlBuilder;
import com.ahao.haochat.common.common.utils.IpUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.util.Optional;

public class HttpHeadersHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            UrlBuilder urlBuilder = UrlBuilder.ofHttp(request.uri());

            // 获取token参数
            String token = Optional.ofNullable(urlBuilder.getQuery()).map(k->k.get("token")).map(CharSequence::toString).orElse("");
            NettyUtil.setAttr(ctx.channel(), NettyUtil.TOKEN, token);

            // 获取请求路径
            request.setUri(urlBuilder.getPath().toString());
            HttpHeaders headers = request.headers();
            // 按优先级获取真实 IP：X-Forwarded-For → X-Real-IP → RemoteAddr
            String xForwardedFor = headers.get("X-Forwarded-For");
            String xRealIp = headers.get("X-Real-IP");
            String remoteAddr = null;
            if (ctx.channel().remoteAddress() instanceof InetSocketAddress) {
                remoteAddr = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
            }
            String ip = IpUtils.getClientIpFromHeaders(xForwardedFor, xRealIp, remoteAddr);
            NettyUtil.setAttr(ctx.channel(), NettyUtil.IP, ip);
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
        }else
        {
            ctx.fireChannelRead(msg);
        }
    }
}