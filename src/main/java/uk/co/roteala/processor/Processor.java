package uk.co.roteala.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;

import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageTemplate;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.exceptions.MessageSerializationException;
import uk.co.roteala.exceptions.errorcodes.MessageSerializationErrCode;
import uk.co.roteala.utils.BlockchainUtils;


public interface Processor {
    void process(Message message);

    /**
     * Modify so that it sends JSON instead of a full object
     * */
    default Message mapper(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        String messageWrapperString = SerializationUtils.deserialize(bytes);
        ReferenceCountUtil.release(byteBuf);

        MessageTemplate.MessageTemplateBuilder templateBuilder = MessageTemplate.builder();

        try {

            ObjectMapper objectMapper = new ObjectMapper();
            MessageWrapper messageWrapper = objectMapper.readValue(messageWrapperString, MessageWrapper.class);

            templateBuilder
                    .verified(messageWrapper.isVerified())
                    .messageAction(messageWrapper.getAction());

            switch (messageWrapper.getType()) {
                case PEERS:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.PEERS);
                    break;
                case BLOCK:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.BLOCK);
                    break;
                case BLOCKHEADER:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.BLOCKHEADER);
                    break;
                case ACCOUNT:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.ACCOUNT);
                    break;
                case MEMPOOL:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.MEMPOOL);
                    break;
                case STATECHAIN:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.STATECHAIN);
                    break;
                case TRANSACTION:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.TRANSACTION);
                    break;
                case NODESTATE:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.NODESTATE);
            }

            return templateBuilder.build();

        } catch (Exception e) {
            throw new MessageSerializationException(MessageSerializationErrCode.DESERIALIZATION_FAILED);
        }
    }
}
