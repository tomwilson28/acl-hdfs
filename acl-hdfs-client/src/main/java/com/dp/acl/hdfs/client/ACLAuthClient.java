package com.dp.acl.hdfs.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.dp.acl.hdfs.core.MultiAuthRequest;
import com.dp.acl.hdfs.core.MultiAuthRequestEncoder;
import com.dp.acl.hdfs.core.MultiAuthResponse;
import com.dp.acl.hdfs.core.MultiAuthResponseDecoder;

public class ACLAuthClient {
	
	private BlockingQueue<MultiAuthRequest> requestQueue = new LinkedBlockingQueue<MultiAuthRequest>();
	private Map<MultiAuthRequest, MultiAuthResponse> resultMap = new ConcurrentHashMap<MultiAuthRequest, MultiAuthResponse>();
	
	private final String host;
	private final int port;
	
	private Channel ch;
	private ACLAuthRequestSender sender;
	
	public ACLAuthClient(String host, int port) throws Exception{
		this.host = host;
		this.port = port;
		run();
	}
	
	public void run() throws InterruptedException{
		EventLoopGroup group = new NioEventLoopGroup();
		try{
			Bootstrap b = new Bootstrap();
			b.group(group)
			 .channel(NioSocketChannel.class)
			 .handler(new ChannelInitializer<SocketChannel>(){

				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipeline = ch.pipeline();
					pipeline.addLast(new MultiAuthRequestEncoder());
					pipeline.addLast(new MultiAuthResponseDecoder());
					pipeline.addLast(new ACLAuthClientHandler(resultMap));
				}
				 
			 });
			
			ch = b.connect(host, port).sync().channel();
			sender = new ACLAuthRequestSender(ch, requestQueue);
			sender.start();
		}finally{
			group.shutdownGracefully();
		}
	}
	
	public void close() throws InterruptedException{
		ch.closeFuture().sync();
		sender.close();
	}
	
	public MultiAuthResponse send(MultiAuthRequest request, long timeoutInSecond) throws InterruptedException{
		long startTime = System.currentTimeMillis();
		requestQueue.offer(request);
		while(System.currentTimeMillis() - startTime < timeoutInSecond * 1000){
			MultiAuthResponse response = resultMap.get(request);
			if(response != null)
				return response;
			else
				Thread.sleep(100);
		}
		return null;
	}

}