package uk.co.roteala.processor;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.SerializationUtils;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.events.Message;

public interface Processor {
    void process(Message message);

    default Message mapToMessage(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        Message message = SerializationUtils.deserialize(bytes);
        ReferenceCountUtil.release(byteBuf);

        return message;
    }
}
