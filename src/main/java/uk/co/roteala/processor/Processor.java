package uk.co.roteala.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;

import uk.co.roteala.common.events.*;
import uk.co.roteala.exceptions.MessageSerializationException;
import uk.co.roteala.exceptions.errorcodes.MessageSerializationErrCode;
import uk.co.roteala.utils.BlockchainUtils;

import java.nio.charset.StandardCharsets;


public interface Processor {
    void process(Message message);

    /**
     * Modify so that it sends JSON instead of a full object
     * */
    default Message mapper(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        try {
            String messageWrapperString = SerializationUtils.deserialize(bytes);
            ReferenceCountUtil.release(byteBuf);
            // Convert bytes to String

            ObjectMapper objectMapper = new ObjectMapper();
            MessageWrapper messageWrapper = objectMapper.readValue(messageWrapperString, MessageWrapper.class);

            MessageTemplate.MessageTemplateBuilder templateBuilder = MessageTemplate.builder()
                    .verified(messageWrapper.isVerified())
                    .messageAction(messageWrapper.getAction())
                    .content(messageWrapper.getContent())
                    .type(messageWrapper.getType());

            return templateBuilder.build();

        } catch (Exception e) {
            return defaultMessage();
        }
    }

    default Message defaultMessage() {
        return MessageTemplate.builder()
                .type(MessageTypes.DEFAULT)
                .messageAction(MessageActions.DEFAULT)
                .verified(false)
                .content(null)
                .build();
    }
}
