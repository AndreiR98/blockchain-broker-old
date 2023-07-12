package uk.co.roteala.processor;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.storage.StorageServices;

@Slf4j
@RequiredArgsConstructor
public class MessageProcessor implements Processor {
    private final TransmissionHandler transmissionHandler;
    private final StorageServices storageServices;

    //Process the incoming flux of messages
    @Override
    public void process() {
    }
}
