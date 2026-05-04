package com.example.demo.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.DocumentCreateEntity;
import com.arangodb.model.AqlQueryOptions;
import com.arangodb.model.DocumentReadOptions;
import com.example.demo.model.User;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final ArangoDatabase db;
    private static final String COL = "users";

    public User findByKey(String key) {
        return db.collection(COL).getDocument(key, User.class);
    }

    public User findByKeyWithTx(String key, String txId) {
        return db.collection(COL).getDocument(key, User.class,
                new DocumentReadOptions().streamTransactionId(txId));
    }

    // ---- Tìm user theo email (để đăng nhập) ----
    public User findByEmail(String email) {
        String aql = "FOR u IN users FILTER u.email == @email RETURN u";
        Map<String, Object> bind = new HashMap<>();
        bind.put("email", email);
        ArangoCursor<User> cursor = db.query(aql, bind, null, User.class);
        List<User> results = cursor.asListRemaining();
        return results.isEmpty() ? null : results.get(0);
    }

    // ---- Kiểm tra email đã tồn tại ----
    public boolean existsByEmail(String email) {
        return findByEmail(email) != null;
    }

    public User findByUsername(String username) {
        String aql = "FOR u IN users FILTER u.username == @username RETURN u";
        Map<String, Object> bind = new HashMap<>();
        bind.put("username", username);
        ArangoCursor<User> cursor = db.query(aql, bind, null, User.class);
        List<User> results = cursor.asListRemaining();
        return results.isEmpty() ? null : results.get(0);
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    public User save(User user) {
        DocumentCreateEntity<User> result = db.collection(COL).insertDocument(user);
        user.setKey(result.getKey());
        user.setId(result.getId());
        return user;
    }

    // ---- Cộng tiền chi tiêu + cập nhật rank trong transaction ----
    public void addSpendingWithTx(String userId, double amount, String txId) {
        String aql =
            "FOR u IN users FILTER u._key == @uid " +
            "UPDATE u WITH { totalSpent: u.totalSpent + @amount } IN users";
        Map<String, Object> bind = new HashMap<>();
        bind.put("uid", userId);
        bind.put("amount", amount);
        db.query(aql, bind, new AqlQueryOptions().streamTransactionId(txId), Void.class);
    }

    // ============================================================
    // TRIGGER SIMULATION:
    // Cập nhật memberRank dựa trên totalSpent.
    // Method này được gọi TỰ ĐỘNG sau mỗi giao dịch đặt vé thành công
    // – mô phỏng hành vi AFTER INSERT/UPDATE TRIGGER trong SQL Database.
    //
    // Quy tắc phân hạng:
    //   totalSpent >= 10.000.000 VND → Premium
    //   totalSpent >=  5.000.000 VND → VIP
    //   totalSpent  <  5.000.000 VND → Normal
    // ============================================================
    public void updateMemberRank(String userId, String newRank) {
        String aql =
            "FOR u IN users FILTER u._key == @uid " +
            "UPDATE u WITH { memberRank: @rank } IN users";
        Map<String, Object> bind = new HashMap<>();
        bind.put("uid", userId);
        bind.put("rank", newRank);
        db.query(aql, bind, null, Void.class);
    }

    public String calculateRankByStoredProcedure(double totalSpent) {
        String aql = "RETURN CINEMA::SP_USER_RANK(@totalSpent)";
        Map<String, Object> bind = new HashMap<>();
        bind.put("totalSpent", totalSpent);
        ArangoCursor<String> cursor = db.query(aql, bind, null, String.class);
        return cursor.hasNext() ? cursor.next() : "normal";
    }

    public List<User> findAll() {
        String aql = "FOR u IN users SORT u.name ASC RETURN u";
        ArangoCursor<User> cursor = db.query(aql, null, null, User.class);
        return cursor.asListRemaining();
    }
}
