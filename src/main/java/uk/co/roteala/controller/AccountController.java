package uk.co.roteala.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import uk.co.roteala.api.ApiError;
import uk.co.roteala.api.account.AccountRequest;
import uk.co.roteala.api.account.AccountResponse;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.AccountMessage;
import uk.co.roteala.common.events.ChainStateMessage;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.handlers.TransmissionHandler;
import uk.co.roteala.services.AccountServices;
import uk.co.roteala.storage.StorageServices;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/account")
@AllArgsConstructor
@Tag(name = "Blockchain Account Operations", description = "The API to fetch info regarding account")
public class AccountController {
    final AccountServices accountServices;

    @Operation(summary = "Get account details")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountRequest.class)), required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account successfully retrieved", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = AccountResponse.class))}),
            @ApiResponse(responseCode = "404", description = "Invalid account data", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))}),
            @ApiResponse(responseCode = "400", description = "BadRequest", content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiError.class))})})
    @GetMapping("/addresses")
    @ResponseStatus(HttpStatus.OK)
    public AccountResponse sendTransaction(@Valid @org.springframework.web.bind.annotation.RequestBody AccountRequest accountRequest){
        return this.accountServices.getAccount(accountRequest);
    }
}
//
//    //public AccountModel getAccountDetails(){}
//    @Autowired
//    private TransmissionHandler transmissionHandler;
//
//    private final StorageServices storage;
//
//    @GetMapping("/create")
//    public String createNewAccount() throws JsonProcessingException {
//        ChainState chainState = null;
//        try {
//            chainState = storage.getStateTrie();
//        } catch (RocksDBException e) {
//            throw new RuntimeException(e);
//        }
//
//        List<AccountMessage> accounts = new ArrayList<>();
//
//        chainState.getAccounts().forEach(address -> {
//            AccountModel account = null;
//            try {
//                account = storage.getAccountByAddress(address);
//            } catch (RocksDBException e) {
//                throw new RuntimeException(e);
//            }
//
//            accounts.add(new AccountMessage(account));
//        });
//
//        Flux<Message> accountMessageFlux = Flux.fromIterable(accounts);
//
//        Mono<Message> chainStateMessageMono = Mono.just(new ChainStateMessage(chainState));
//
//        accountMessageFlux.concatWith(chainStateMessageMono)
//                .delayElements(Duration.ofMillis(500))
//                        .flatMap(message -> transmissionHandler.sendData(message))
//                                .subscribe();
//
////        BigInteger x = new BigInteger("15214694582478001223698357172807149599333118959163503148786291364719972344037", 10);
////        BigInteger s = new BigInteger("90376737993072300104819040357024215956870474498641858620598082591276763877280", 10);
////        BigInteger h = new BigInteger("43507248521930584608553320857595913329369016028856864660635270168824312350546", 10);
////
////        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
////        //ECPoint point =  ecSpec.getCurve().decodePoint(x.toByteArray());
////
////        X9ECParameters curveRaw = SECNamedCurves.getByName("secp256k1");
////
////        ECCurve curve = curveRaw.getCurve();
////
////        // Compute the y-coordinate
////        ECFieldElement xFieldElement = curve.fromBigInteger(x);
////        ECPoint rPoint = solveYCoordinate(curve, xFieldElement);
////
////        BigInteger sInverted = s.modInverse(curve.getOrder());
////        BigInteger rInverted = x.modInverse(curve.getOrder());
////
////        BigInteger rsInverted = x.multiply(sInverted).modInverse(curve.getOrder());
////
////        BigInteger gCoeficient = rInverted.multiply(h).mod(curve.getOrder());
////
////        ECPoint pu1 = rPoint.multiply(rsInverted).negate();
////        ECPoint pu2 = curveRaw.getG().multiply(gCoeficient).negate();
////
////        ECPoint publicKeyPoint = pu1.add(pu2).normalize();
////
////        log.info("Point1:{}, {}", publicKeyPoint.getAffineXCoord().toBigInteger(), publicKeyPoint.getAffineYCoord().toBigInteger());
////        log.info("Decoded:{}", CryptographyUtils.decodeAddress("1E5k58XvjUgY6TpurigV3jzY2iUUSZGwLS"));
//
//        SignatureModel signature = new SignatureModel(
//                "ef05c205d267249ffa70b830b69f6fbbf26198f9e82d6adf4c0d49ea6303fa82",
//                "f653a822c42053a23a1dff8e5e203ecedd59e22aefa7a4cf1a0908bf10aa6a1b");
//
//        PseudoTransaction tx = new PseudoTransaction();
//        tx.setFrom("1L8pa8DQyuVaHCgR2UN71Mzy3KnNX68Y1a");
//        tx.setPseudoHash("81a85ed916f6e356b92a39c268b6f069c6c1e1fcfc46b646f9e5249f0002b9d8");
//        tx.setSignature(signature);
//        tx.setStatus(TransactionStatus.PENDING);
//        tx.setTo("1Di9UDgrVdEA7Vk3NUhEUi2XqPw8TRcuG8");
//        tx.setTimeStamp(1688143810);
//        tx.setPubKeyHash("786a33e9e10673747d9021c8478e1e5572c6fcd346e79f6b1184aeeb8e7b3f20");
//        tx.setNonce(0);
//        tx.setValue(Coin.valueOf(new BigDecimal("123")));
//        tx.setVersion(0x10);
//
//        log.info("Test:{}", tx.verifySignatureWithRecovery());
//
//        return tx.computeHash();
//
//
//        //log.info("The point:{}, y:{}", positivePoint.getAffineYCoord().toBigInteger(), negativePoint.getAffineYCoord().toBigInteger());
//
////        Flux.fromIterable(chainState.getAccounts())
////                .flatMap(address -> Mono.fromCallable(() -> storage.getAccountByAddress(address))
////                        .onErrorMap(RocksDBException.class, RuntimeException::new)
////                )
////                .delayElements(Duration.ofMillis(500))
////                .flatMap(account -> transmissionHandler.sendData(account))
////                .then()
////                .concatWith(chainStateMessageMono))
////                .flatMap();
//    }
//
//    private static String bytesToHexString(byte[] bytes) {
//        StringBuilder hexString = new StringBuilder();
//        for (byte b : bytes) {
//            String hex = String.format("%02x", b);
//            hexString.append(hex);
//        }
//        return hexString.toString();
//    }
//
//    private static ECPoint solveYCoordinate(ECCurve curve, ECFieldElement x) {
//        ECFieldElement ySquare = x.square().multiply(x).add(curve.getA()).multiply(x).add(curve.getB());
//
//        BigInteger beta = x.toBigInteger()
//                .pow(3)
//                .add(x.toBigInteger().multiply(curve.getA().toBigInteger()))
//                .add(curve.getB().toBigInteger());
//
//        BigInteger y = beta
//                .modPow(curve.getField().getCharacteristic()
//                        .add(BigInteger.ONE)
//                        .divide(BigInteger.valueOf(4)), curve.getField().getCharacteristic());
//
//        return curve.createPoint(x.toBigInteger(), y);
//    }

