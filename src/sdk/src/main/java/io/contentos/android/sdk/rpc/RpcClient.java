package io.contentos.android.sdk.rpc;

import com.google.protobuf.ByteString;
import io.contentos.android.sdk.prototype.Transaction.signed_transaction;
import io.contentos.android.sdk.prototype.Type;
import io.contentos.android.sdk.rpc.Grpc.*;
import io.contentos.android.sdk.prototype.MultiId.*;

/**
 * The RPC Client.
 * <p>It implements {@link Operation.BaseResultFilter} for easy transaction building and sending.
 * For example, to send a transfer transaction, simply call
 * <pre>
 *     client.transfer(...);
 * </pre>
 * {@link #transfer} will generate a transaction containing a single transfer operation, sign it with
 * signing key, call {@link #broadcastTrx} and return the response.
 * </p>
 *
 * <p>In rare cases, when you have to send a transaction consisting of multiple operations, try the
 * following codes,
 * <pre>
 *     // add operations
 *     trx = new Transaction().transfer(...)
 *                            .vote(...)
 *                            .createAccount(...);
 *
 *     client.signAndBroadcastTrx(trx, true);
 * </pre>
 * </p>
 */
public class RpcClient extends Operation.BaseResultFilter<Transaction, Transaction, BroadcastTrxResponse> {
    
    protected ApiServiceGrpc.ApiServiceBlockingStub service;
    protected String signingKey;

    /**
     * Create an instance of RPC client.
     * @param service       the gRPC service
     * @param signingKey    the signing private key for transactions
     */
    public RpcClient(ApiServiceGrpc.ApiServiceBlockingStub service, String signingKey) {
        super(new Transaction.Factory());
        this.service = service;
        this.signingKey = signingKey;
    }

    /**
     * Create an instance of RPC client.
     * @param service the gRPC service
     */
    public RpcClient(ApiServiceGrpc.ApiServiceBlockingStub service) {
        this(service, null);
    }
    
    /**
     * Override method of {@link Operation.BaseResultFilter#filterResult} to sign and broadcast a transaction.
     * @param trx the transaction to sign and broadcast
     * @return response of broadcastTrx API
     */
    @Override
    protected BroadcastTrxResponse filterResult(Transaction trx) {
        return signAndBroadcastTrx(trx, true);
    }
    
    /**
     * Query a smart contract's database table.
     * @param owner     name of contract owner account
     * @param contract  name of contract
     * @param table     name of table
     * @param field     name of record field to query
     * @param begin     query value in JSON
     * @param count     maximum number of returned records
     * @param reverse   result order, if set, in descending order, otherwise ascending order.
     * @return query result.
     */
    public TableContentResponse queryTableContent(String owner, String contract, String table, String field, String begin, int count, boolean reverse) {
        return service.queryTableContent(
                GetTableContentRequest.newBuilder()
                        .setOwner(owner)
                        .setContract(contract)
                        .setTable(table)
                        .setField(field)
                        .setBegin(begin)
                        .setCount(count)
                        .setReverse(reverse)
                        .build()
        );
    }

    /**
     * Get account information of given account name.
     * @param accountName account name
     * @return account information.
     */
    public AccountResponse getAccountByName(String accountName) {
        return service.getAccountByName(
                GetAccountByNameRequest.newBuilder()
                        .setAccountName(accountName(accountName))
                        .build()
        );
    }

    /**
     * Get rewards of specific account.
     * @param accountName  the account
     * @return the reward.
     */
    public AccountRewardResponse getAccountRewardByName(String accountName) {
        return service.getAccountRewardByName(
                GetAccountRewardByNameRequest.newBuilder()
                        .setAccountName(accountName(accountName))
                        .build()
        );
    }

    /**
     * Get reward to a specific account.
     * @param accountName   the account
     * @param postId        the post id rewards belong to
     * @return reward vesting
     */
    public AccountCashoutResponse getAccountCashout(String accountName, long postId) {
        return service.getAccountCashout(
                GetAccountCashoutRequest.newBuilder()
                        .setAccountName(accountName(accountName))
                        .setPostId(postId)
                        .build()
        );
    }

    /**
     * Get reward records of specific block.
     * @param blockHeight the block height. e.g. block number
     * @return list of reward records.
     */
    public BlockCashoutResponse getBlockCashout(long blockHeight) {
        return service.getBlockCashout(
                GetBlockCashoutRequest.newBuilder()
                        .setBlockHeight(blockHeight)
                        .build()
        );
    }

    /**
     * Get follower list of specific account.
     * @param accountName   the account being followed
     * @param pageSize      maximum items in a page
     * @return follower list in descending order of follow-ship creation time.
     */
    public RpcResultPages<GetFollowerListByNameResponse, follower_created_order, follower_created_order> getFollowerListByName(String accountName, int pageSize) {
        follower_created_order.Builder query = follower_created_order.newBuilder()
                .setAccount(accountName(accountName));

        return new RpcResultPages<GetFollowerListByNameResponse, follower_created_order, follower_created_order>(
                query.clone().setCreatedTime(minTimeStamp).build(),
                query.clone().setCreatedTime(maxTimeStamp).build(),
                pageSize)
        {
            @Override
            protected GetFollowerListByNameResponse request(follower_created_order start, follower_created_order end, int count, follower_created_order last) {
                GetFollowerListByNameRequest.Builder b = GetFollowerListByNameRequest.newBuilder();
                b.setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastOrder(last);
                }
                return service.getFollowerListByName(b.build());
            }

            @Override
            protected follower_created_order getLastItem(GetFollowerListByNameResponse resp) {
                return isEmptyResponse(resp)? null : resp.getFollowerList(resp.getFollowerListCount() - 1).getCreateOrder();
            }

            @Override
            protected boolean isEmptyResponse(GetFollowerListByNameResponse resp) {
                return resp == null || resp.getFollowerListCount() == 0;
            }
        };
    }

    /**
     * Get followee list of specific account
     * @param accountName  the follower account
     * @param pageSize     maximum items in a page
     * @return list of accounts followed by the account, in descending order of follow-ship creation time.
     */
    public RpcResultPages<GetFollowingListByNameResponse, following_created_order, following_created_order> getFollowingListByName(String accountName, int pageSize) {
        following_created_order.Builder query = following_created_order.newBuilder()
                .setAccount(accountName(accountName));

        return new RpcResultPages<GetFollowingListByNameResponse, following_created_order, following_created_order>(
                query.clone().setCreatedTime(minTimeStamp).build(),
                query.clone().setCreatedTime(maxTimeStamp).build(),
                pageSize)
        {
            @Override
            protected GetFollowingListByNameResponse request(following_created_order start, following_created_order end, int count, following_created_order last) {
                GetFollowingListByNameRequest.Builder b = GetFollowingListByNameRequest.newBuilder();
                b.setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastOrder(last);
                }
                return service.getFollowingListByName(b.build());
            }

            @Override
            protected following_created_order getLastItem(GetFollowingListByNameResponse resp) {
                return isEmptyResponse(resp)? null : resp.getFollowingList(resp.getFollowingListCount() - 1).getCreateOrder();
            }

            @Override
            protected boolean isEmptyResponse(GetFollowingListByNameResponse resp) {
                return resp == null || resp.getFollowingListCount() == 0;
            }
        };
    }

    /**
     * Get number of followers and followees of specific account.
     * @param accountName the account
     * @return response containing the counts.
     */
    public GetFollowCountByNameResponse getFollowCountByName(String accountName) {
        return service.getFollowCountByName(
                GetFollowCountByNameRequest.newBuilder()
                        .setAccountName(accountName(accountName))
                        .build()
        );
    }

    /**
     * Get block producers.
     * @param pageSize maximum items in a page
     * @return list of block producers in ascending order of account names.
     */
    public RpcResultPages<GetWitnessListResponse, Void, String> getWitnessList(int pageSize) {
        return new RpcResultPages<GetWitnessListResponse, Void, String>(null, null, pageSize)
        {
            @Override
            protected GetWitnessListResponse request(Void start, Void end, int count, String last) {
                GetWitnessListRequest.Builder b = GetWitnessListRequest.newBuilder();
                b.setLimit(count);
                if (last != null) {
                    b.setStart(accountName(last));
                }
                return service.getWitnessList(b.build());
            }

            @Override
            protected String getLastItem(GetWitnessListResponse resp) {
                return isEmptyResponse(resp)? null : resp.getWitnessList(resp.getWitnessListCount() - 1).getOwner().getValue();
            }

            @Override
            protected boolean isEmptyResponse(GetWitnessListResponse resp) {
                return resp == null || resp.getWitnessListCount() == 0;
            }
        };
    }

    /**
     * Get posts created in specific time range.
     * @param startTimestamp    lower bound of time range, in UTC seconds
     * @param endTimeStamp      upper bound of time range, in UTC seconds
     * @param count             maximum returned items
     * @return list of posts in descending order of creation time.
     */
    public GetPostListByCreatedResponse getPostListByCreated(int startTimestamp, int endTimeStamp, int count) {
        return service.getPostListByCreated(
                GetPostListByCreatedRequest.newBuilder()
                        .setStart(post_created_order.newBuilder()
                                .setCreated(timeStamp(startTimestamp)))
                        .setEnd(post_created_order.newBuilder()
                                .setCreated(timeStamp(endTimeStamp)))
                        .setLimit(count)
                        .build()
        );
    }

    /**
     * Get comments of specific post in a time range.
     * @param parentId          post id of the article being commented
     * @param startTimestamp    lower bound of time range, in UTC seconds
     * @param endTimeStamp      upper bound of time range, in UTC seconds
     * @param count             maximum returned items
     * @return list of comments in descending order of creation time.
     */
    public GetReplyListByPostIdResponse getReplyListByPostId(long parentId, int startTimestamp, int endTimeStamp, int count) {
        return service.getReplyListByPostId(
                GetReplyListByPostIdRequest.newBuilder()
                        .setStart(reply_created_order.newBuilder()
                                .setParentId(parentId)
                                .setCreated(timeStamp(startTimestamp)))
                        .setEnd(reply_created_order.newBuilder()
                                .setParentId(parentId)
                                .setCreated(timeStamp(endTimeStamp)))
                        .setLimit(count)
                        .build()
        );
    }

    /**
     * Get block chain state.
     * @return the state.
     */
    public GetChainStateResponse getChainState() {
        return service.getChainState(
                NonParamsRequest.getDefaultInstance()
        );
    }

    /**
     * Get block chain statistics.
     * @return the stats.
     */
    public GetStatResponse getStatisticsInfo() {
        return service.getStatisticsInfo(
                NonParamsRequest.getDefaultInstance()
        );
    }

    /**
     * Broadcast a signed transaction.
     * @param trx           the signed transaction to broadcast
     * @param waitResult    wait until the transaction processing finished.
     * @return processing result of transaction.
     */
    public BroadcastTrxResponse broadcastTrx(signed_transaction trx, boolean waitResult) {
        return service.broadcastTrx(
                BroadcastTrxRequest.newBuilder()
                        .setOnlyDeliver(!waitResult)
                        .setTransaction(trx)
                        .build()
        );
    }

    /**
     * Sign a transaction and broadcast it.
     * @param trx           the transaction
     * @param waitResult    wait until the transaction processing finished.
     * @return processing result of transaction.
     */
    public BroadcastTrxResponse signAndBroadcastTrx(Transaction trx, boolean waitResult) {
        trx.setDynamicGlobalProps(getChainState().getState().getDgpo());
        String key = this.signingKey;
        if (key == null || key.length() == 0) {
            throw new RuntimeException("signing key not found");
        }
        return broadcastTrx(trx.sign(key, 0), waitResult);
    }

    /**
     * Get blocks.
     * @param startBlockNum minimal block number, inclusive
     * @param endBlockNum   maximum block number, exclusive
     * @param count maximum number of returned blocks
     * @return block list in ascending order of block number.
     */
    public GetBlockListResponse getBlockList(long startBlockNum, long endBlockNum, int count) {
        return service.getBlockList(
                GetBlockListRequest.newBuilder()
                        .setStart(startBlockNum)
                        .setEnd(endBlockNum)
                        .setLimit(count)
                        .build()
        );
    }

    /**
     * Get a block.
     * @param blockNum the block number.
     * @return the block.
     */
    public GetSignedBlockResponse getSignedBlock(long blockNum) {
        return service.getSignedBlock(
                GetSignedBlockRequest.newBuilder()
                        .setStart(blockNum)
                        .build()
        );
    }

    /**
     * Get accounts whose balance is within a specific range.
     * @param minBalance    minimal balance, inclusive
     * @param maxBalance    maximum balance, exclusive
     * @param pageSize      maximum items in a page
     * @return account list in descending order of balance.
     */
    public RpcResultPages<GetAccountListResponse, Type.coin, AccountInfo> getAccountListByBalance(long minBalance, long maxBalance, int pageSize) {
        return new RpcResultPages<GetAccountListResponse, Type.coin, AccountInfo>(
                Type.coin.newBuilder().setValue(minBalance).build(),
                Type.coin.newBuilder().setValue(maxBalance).build(),
                pageSize)
        {
            @Override
            protected GetAccountListResponse request(Type.coin start, Type.coin end, int count, AccountInfo last) {
                GetAccountListByBalanceRequest.Builder b = GetAccountListByBalanceRequest.newBuilder();
                b.setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastAccount(last);
                }
                return service.getAccountListByBalance(b.build());
            }

            @Override
            protected AccountInfo getLastItem(GetAccountListResponse resp) {
                return isEmptyResponse(resp)? null : resp.getList(resp.getListCount() - 1).getInfo();
            }

            @Override
            protected boolean isEmptyResponse(GetAccountListResponse resp) {
                return resp == null || resp.getListCount() == 0;
            }
        };
    }

    /**
     * Get daily stats of transactions.
     * @param pageSize  maximum items in a page
     * @return the stats.
     */
    public RpcResultPages<GetDailyTotalTrxResponse, Void, DailyTotalTrx> getDailyTotalTrxInfo(int pageSize) {
        return new RpcResultPages<GetDailyTotalTrxResponse, Void, DailyTotalTrx>(
                null, null, pageSize)
        {
            @Override
            protected GetDailyTotalTrxResponse request(Void start, Void end, int count, DailyTotalTrx last) {
                GetDailyTotalTrxRequest.Builder b = GetDailyTotalTrxRequest.newBuilder();
                b.setLimit(count);
                if (last != null) {
                    b.setLastInfo(last);
                }
                return service.getDailyTotalTrxInfo(b.build());
            }

            @Override
            protected DailyTotalTrx getLastItem(GetDailyTotalTrxResponse resp) {
                return isEmptyResponse(resp)? null : resp.getList(resp.getListCount() - 1);
            }

            @Override
            protected boolean isEmptyResponse(GetDailyTotalTrxResponse resp) {
                return resp == null || resp.getListCount() == 0;
            }
        };
    }

    /**
     * Get transaction information.
     * @param trxId the transaction id
     * @return transaction information.
     */
    public GetTrxInfoByIdResponse getTrxInfoById(byte[] trxId) {
        return service.getTrxInfoById(
                GetTrxInfoByIdRequest.newBuilder()
                        .setTrxId(Type.sha256.newBuilder().setHash(ByteString.copyFrom(trxId)))
                        .build()
        );
    }

    /**
     * Get transactions created in a specific time range.
     * @param startTimestamp    minimal time stamp, in UTC seconds, inclusive
     * @param endTimeStamp      maximum time stamp, in UTC seconds, exclusive
     * @param pageSize          maximum items in a page
     * @return transactions in descending order of creation time.
     */
    public RpcResultPages<GetTrxListByTimeResponse, Type.time_point_sec, TrxInfo> getTrxListByTime(int startTimestamp, int endTimeStamp, int pageSize) {
        return new RpcResultPages<GetTrxListByTimeResponse, Type.time_point_sec, TrxInfo>(
                timeStamp(startTimestamp),
                timeStamp(endTimeStamp),
                pageSize)
        {
            @Override
            protected GetTrxListByTimeResponse request(Type.time_point_sec start, Type.time_point_sec end, int count, TrxInfo last) {
                GetTrxListByTimeRequest.Builder b = GetTrxListByTimeRequest.newBuilder();
                b.setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastInfo(last);
                }
                return service.getTrxListByTime(b.build());
            }

            @Override
            protected TrxInfo getLastItem(GetTrxListByTimeResponse resp) {
                return isEmptyResponse(resp)? null : resp.getList(resp.getListCount() - 1);
            }

            @Override
            protected boolean isEmptyResponse(GetTrxListByTimeResponse resp) {
                return resp == null || resp.getListCount() == 0;
            }
        };
    }

    /**
     * Get posts created in specific time range.
     * @param startTimestamp    minimal time stamp, in UTC seconds, inclusive
     * @param endTimeStamp      maximum time stamp, in UTC seconds, exclusive
     * @param pageSize          maximum items in a page
     * @return post list in descending order of creation time.
     */
    public RpcResultPages<GetPostListByCreateTimeResponse, Type.time_point_sec, PostResponse> getPostListByCreateTime(int startTimestamp, int endTimeStamp, int pageSize) {
        return new RpcResultPages<GetPostListByCreateTimeResponse, Type.time_point_sec, PostResponse>(
                timeStamp(startTimestamp),
                timeStamp(endTimeStamp),
                pageSize)
        {
            @Override
            protected GetPostListByCreateTimeResponse request(Type.time_point_sec start, Type.time_point_sec end, int count, PostResponse last) {
                GetPostListByCreateTimeRequest.Builder b = GetPostListByCreateTimeRequest.newBuilder();
                b.setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastPost(last);
                }
                return service.getPostListByCreateTime(b.build());
            }

            @Override
            protected PostResponse getLastItem(GetPostListByCreateTimeResponse resp) {
                return isEmptyResponse(resp)? null : resp.getPostedList(resp.getPostedListCount() - 1);
            }

            @Override
            protected boolean isEmptyResponse(GetPostListByCreateTimeResponse resp) {
                return resp == null || resp.getPostedListCount() == 0;
            }
        };
    }

    /**
     * Get recent posts of specific author
     * @param author    the author
     * @param pageSize  maximum items in a page
     * @return post list in descending order of creation time.
     */
    public RpcResultPages<GetPostListByCreateTimeResponse, user_post_create_order, PostResponse> getPostListByName(String author, int pageSize) {
        user_post_create_order.Builder query = user_post_create_order.newBuilder();
        query.setAuthor(accountName(author));

        return new RpcResultPages<GetPostListByCreateTimeResponse, user_post_create_order, PostResponse>(
                query.clone().setCreate(minTimeStamp).build(),
                query.clone().setCreate(maxTimeStamp).build(),
                pageSize)
        {
            @Override
            protected GetPostListByCreateTimeResponse request(user_post_create_order start, user_post_create_order end, int count, PostResponse last) {
                GetPostListByNameRequest.Builder b = GetPostListByNameRequest.newBuilder();
                b.setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastPost(last);
                }
                return service.getPostListByName(b.build());
            }

            @Override
            protected PostResponse getLastItem(GetPostListByCreateTimeResponse resp) {
                return isEmptyResponse(resp)? null : resp.getPostedList(resp.getPostedListCount() - 1);
            }

            @Override
            protected boolean isEmptyResponse(GetPostListByCreateTimeResponse resp) {
                return resp == null || resp.getPostedListCount() == 0;
            }
        };
    }

    /**
     * Get hourly transaction stats.
     * @return the stats.
     */
    public TrxStatByHourResponse trxStatByHour() {
        return service.trxStatByHour(
                TrxStatByHourRequest.newBuilder()
                        .setHours(24)
                        .build()
        );
    }

    /**
     * Get transactions signed by specific account in specific time range.
     * @param name              account name
     * @param startTimestamp    minimal time stamp, in UTC seconds, inclusive
     * @param endTimeStamp      maximum time stamp, in UTC seconds, exclusive
     * @param pageSize          maximum items in a page
     * @return transaction list in descending order of creation time.
     */
    public RpcResultPages<GetUserTrxListByTimeResponse, Type.time_point_sec, TrxInfo> getUserTrxListByTime(final String name, int startTimestamp, int endTimeStamp, int pageSize) {
        return new RpcResultPages<GetUserTrxListByTimeResponse, Type.time_point_sec, TrxInfo>(
                timeStamp(startTimestamp),
                timeStamp(endTimeStamp),
                pageSize)
        {
            @Override
            protected GetUserTrxListByTimeResponse request(Type.time_point_sec start, Type.time_point_sec end, int count, TrxInfo last) {
                GetUserTrxListByTimeRequest.Builder b = GetUserTrxListByTimeRequest.newBuilder();
                b.setName(accountName(name)).setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastTrx(last);
                }
                return service.getUserTrxListByTime(b.build());
            }

            @Override
            protected TrxInfo getLastItem(GetUserTrxListByTimeResponse resp) {
                return isEmptyResponse(resp)? null : resp.getTrxList(resp.getTrxListCount() - 1);
            }

            @Override
            protected boolean isEmptyResponse(GetUserTrxListByTimeResponse resp) {
                return resp == null || resp.getTrxListCount() == 0;
            }
        };
    }

    /**
     * Get post information.
     * @param postId  post id
     * @return post information.
     */
    public GetPostInfoByIdResponse getPostInfoById(long postId) {
        return service.getPostInfoById(
                GetPostInfoByIdRequest.newBuilder()
                        .setPostId(postId)
                        .setReplyListLimit(100)
                        .setVoterListLimit(100)
                        .build()
        );
    }

    /**
     * Get smart contract information.
     * @param owner     contract owner account
     * @param contract  contract name
     * @return contract information.
     */
    public GetContractInfoResponse getContractInfo(String owner, String contract) {
        return service.getContractInfo(
                GetContractInfoRequest.newBuilder()
                        .setOwner(accountName(owner))
                        .setContractName(contract)
                        .setFetchAbi(true)
                        .setFetchCode(true)
                        .build()
        );
    }

    /**
     * Check if specific transaction is in an irreversible block.
     * @param trxId  transaction id
     * @return checking result.
     */
    public GetBlkIsIrreversibleByTxIdResponse getBlkIsIrreversibleByTxId(byte[] trxId) {
        return service.getBlkIsIrreversibleByTxId(
                GetBlkIsIrreversibleByTxIdRequest.newBuilder()
                        .setTrxId(Type.sha256.newBuilder().setHash(
                                ByteString.copyFrom(trxId)
                        ))
                        .build()
        );
    }

    /**
     * Get accounts created in specific time range.
     * @param startTimestamp    minimal time stamp, in UTC seconds, inclusive
     * @param endTimeStamp      maximum time stamp, in UTC seconds, exclusive
     * @param pageSize          maximum items in a page
     * @return account list in descending order of creation time.
     */
    public RpcResultPages<GetAccountListResponse, Type.time_point_sec, AccountInfo> getAccountListByCreTime(int startTimestamp, int endTimeStamp, int pageSize) {
        return new RpcResultPages<GetAccountListResponse, Type.time_point_sec, AccountInfo>(
                timeStamp(startTimestamp),
                timeStamp(endTimeStamp),
                pageSize)
        {
            @Override
            protected GetAccountListResponse request(Type.time_point_sec start, Type.time_point_sec end, int count, AccountInfo last) {
                GetAccountListByCreTimeRequest.Builder b = GetAccountListByCreTimeRequest.newBuilder();
                b.setStart(start).setEnd(end).setLimit(count);
                if (last != null) {
                    b.setLastAccount(last);
                }
                return service.getAccountListByCreTime(b.build());
            }

            @Override
            protected AccountInfo getLastItem(GetAccountListResponse resp) {
                return isEmptyResponse(resp)? null : resp.getList(resp.getListCount() - 1).getInfo();
            }

            @Override
            protected boolean isEmptyResponse(GetAccountListResponse resp) {
                return resp == null || resp.getListCount() == 0;
            }
        };
    }

    //
    // Helpers for cleaner codes
    //

    private static Type.time_point_sec timeStamp(int utcSeconds) {
        return Type.time_point_sec.newBuilder().setUtcSeconds(utcSeconds).build();
    }

    private static Type.time_point_sec minTimeStamp = timeStamp(0);
    private static Type.time_point_sec maxTimeStamp = timeStamp(Integer.MAX_VALUE);

    private static Type.account_name accountName(String name) {
        return Type.account_name.newBuilder().setValue(name).build();
    }
}