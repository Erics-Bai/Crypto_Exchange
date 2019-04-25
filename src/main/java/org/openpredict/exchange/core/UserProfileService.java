package org.openpredict.exchange.core;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.openpredict.exchange.beans.UserProfile;
import org.openpredict.exchange.beans.cmd.CommandResultCode;

/**
 * Stateful (!) User profile service
 * <p>
 * TODO make multi instance
 */
@Slf4j
public final class UserProfileService {

    /**
     * State: uid -> user profile
     */
    private final MutableLongObjectMap<UserProfile> userProfiles = new LongObjectHashMap<>();

    /**
     * Find user profile
     *
     * @param uid
     * @return
     */
    public UserProfile getUserProfile(long uid) {
        return userProfiles.get(uid);
    }

    public UserProfile getUserProfileOrThrowEx(long uid) {

        final UserProfile userProfile = userProfiles.get(uid);

        if (userProfile == null) {
            throw new IllegalStateException("User profile not found, uid=" + uid);
        }

        return userProfile;
    }


    /**
     * Perform balance adjustment for specific user
     *
     * @param uid
     * @param currency
     * @param amount
     * @param fundingTransactionId
     * @return result code
     */
    public CommandResultCode balanceAdjustment(final long uid, final int currency, final long amount, final long fundingTransactionId) {

        final UserProfile userProfile = getUserProfile(uid);
        if (userProfile == null) {
            log.warn("User profile {} not found", uid);
            return CommandResultCode.AUTH_INVALID_USER;
        }

        if (amount == 0) {
            return CommandResultCode.USER_MGMT_ACCOUNT_BALANCE_ADJUSTMENT_ZERO;
        }

        // double settlement protection
        if (userProfile.externalTransactions.contains(fundingTransactionId)) {
            return CommandResultCode.USER_MGMT_ACCOUNT_BALANCE_ADJUSTMENT_ALREADY_APPLIED;
        }

        if (currency == 0) {
            if (userProfile.futuresBalance + amount < 0) {
                return CommandResultCode.USER_MGMT_ACCOUNT_BALANCE_ADJUSTMENT_NSF;
            } else {

                userProfile.externalTransactions.add(fundingTransactionId);
                userProfile.futuresBalance += amount;
                return CommandResultCode.SUCCESS;
            }

        } else {
            if (amount < 0 && userProfile.accounts.get(currency) + amount < 0) {
                return CommandResultCode.USER_MGMT_ACCOUNT_BALANCE_ADJUSTMENT_NSF;
            } else {

                userProfile.externalTransactions.add(fundingTransactionId);
                userProfile.accounts.addToValue(currency, amount);
                return CommandResultCode.SUCCESS;
            }
        }
    }

    /**
     * Create a new user profile with known unique uid
     *
     * @param uid
     * @return
     */
    public CommandResultCode addEmptyUserProfile(long uid) {
        if (userProfiles.get(uid) != null) {
            log.debug("Can not add user, already exists: {}", uid);
            return CommandResultCode.USER_MGMT_USER_ALREADY_EXISTS;
        }
        userProfiles.put(uid, new UserProfile(uid));
        return CommandResultCode.SUCCESS;
    }

    public void reset() {
        userProfiles.clear();
    }

}