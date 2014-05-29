package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.Constants;
import nxt.DigitalGoodsStore;
import nxt.NxtException;
import nxt.crypto.EncryptedData;
import nxt.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

import static nxt.http.JSONResponses.ALREADY_DELIVERED;
import static nxt.http.JSONResponses.INCORRECT_DGS_DISCOUNT;
import static nxt.http.JSONResponses.INCORRECT_DGS_GOODS;
import static nxt.http.JSONResponses.INCORRECT_PURCHASE;

public final class DGSDelivery extends CreateTransaction {

    static final DGSDelivery instance = new DGSDelivery();

    private DGSDelivery() {
        super("purchase", "discountNQT", "goodsData", "goodsText", "encryptedGoodsData", "encryptedGoodsNonce");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        Account sellerAccount = ParameterParser.getSenderAccount(req);
        DigitalGoodsStore.Purchase purchase = ParameterParser.getPurchase(req);
        if (! sellerAccount.getId().equals(purchase.getSellerId())) {
            return INCORRECT_PURCHASE;
        }
        if (! purchase.isPending()) {
            return ALREADY_DELIVERED;
        }

        String discountValueNQT = Convert.emptyToNull(req.getParameter("discountNQT"));
        long discountNQT = 0;
        try {
            if (discountValueNQT != null) {
                discountNQT = Long.parseLong(discountValueNQT);
            }
        } catch (RuntimeException e) {
            return INCORRECT_DGS_DISCOUNT;
        }
        if (discountNQT < 0 || discountNQT > Constants.MAX_BALANCE_NQT) {
            return INCORRECT_DGS_DISCOUNT;
        }

        Account buyerAccount = Account.getAccount(purchase.getBuyerId());
        EncryptedData encryptedGoods = ParameterParser.getEncryptedGoods(req);

        if (encryptedGoods == null) {
            String secretPhrase = ParameterParser.getSecretPhrase(req);
            byte[] goodsData;
            try {
                String goodsDataString = Convert.emptyToNull(req.getParameter("goodsData"));
                if (goodsDataString != null) {
                    goodsData = Convert.parseHexString(goodsDataString);
                } else {
                    goodsData = Convert.nullToEmpty(req.getParameter("goodsText")).getBytes("UTF-8");
                }
            } catch (UnsupportedEncodingException|RuntimeException e) {
                return INCORRECT_DGS_GOODS;
            }
            encryptedGoods = buyerAccount.encryptTo(goodsData, secretPhrase);
        }

        Attachment attachment = new Attachment.DigitalGoodsDelivery(purchase.getId(), encryptedGoods, discountNQT);
        return createTransaction(req, sellerAccount, attachment);

    }

}