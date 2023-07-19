package uk.co.roteala.processor;

import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;

public interface Processor {
    void process(NettyInbound inbound, NettyOutbound outbound);
}
