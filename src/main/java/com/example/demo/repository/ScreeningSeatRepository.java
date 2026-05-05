package com.example.demo.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.model.DocumentReadOptions;
import com.example.demo.model.ScreeningSeat;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScreeningSeatRepository {

    private final ArangoDatabase db;
    private static final String COL = "seats";

    // ---- Lấy tất cả ghế của một suất chiếu (sắp xếp theo row, col) ----
    public List<ScreeningSeat> findByScreeningId(String screeningId) {
        String aql =
            "LET screening = DOCUMENT('screenings', @sid) " +
            "FOR s IN seats " +
            "FILTER s.room_key == screening.room_key " +
            "LET edge = FIRST(" +
            "  FOR e IN booking_seats " +
            "  FILTER e._to == s._id AND e.screening_key == @sid AND (" +
            "    e.booking_status == 'confirmed' OR " +
            "    (e.booking_status == 'holding' AND e.hold_expires_at != null AND DATE_TIMESTAMP(e.hold_expires_at) > DATE_NOW())" +
            "  ) " +
            "  LIMIT 1 RETURN e" +
            ") " +
            "SORT s.seat_row ASC, s.seat_number ASC " +
            "RETURN { " +
            "  _key: s._key, _id: s._id, screeningId: @sid, " +
            "  row: s.seat_row, col: s.seat_number - 1, seatLabel: s.seat_label, " +
            "  status: edge == null ? 'available' : 'booked', " +
            "  bookingId: edge == null ? null : PARSE_IDENTIFIER(edge._from).key " +
            "}";
        Map<String, Object> bind = new HashMap<>();
        bind.put("sid", screeningId);
        ArangoCursor<ScreeningSeat> cursor = db.query(aql, bind, null, ScreeningSeat.class);
        return cursor.asListRemaining();
    }

    public ScreeningSeat findByKey(String key) {
        BaseDocument doc = db.collection(COL).getDocument(key, BaseDocument.class);
        if (doc == null) return null;

        ScreeningSeat seat = new ScreeningSeat();
        seat.setKey(doc.getKey());
        seat.setId(doc.getId());
        seat.setRow(toInt(doc.getAttribute("seat_row")));
        seat.setCol(toInt(doc.getAttribute("seat_number")) - 1);
        seat.setSeatLabel(String.valueOf(doc.getAttribute("seat_label")));
        seat.setStatus("available");
        return seat;
    }

    // ---- Dùng trong transaction ----
    public ScreeningSeat findByKeyWithTx(String key, String txId) {
        BaseDocument doc = db.collection(COL).getDocument(
                key,
                BaseDocument.class,
                new DocumentReadOptions().streamTransactionId(txId)
        );
        if (doc == null) return null;

        ScreeningSeat seat = new ScreeningSeat();
        seat.setKey(doc.getKey());
        seat.setId(doc.getId());
        seat.setRow(toInt(doc.getAttribute("seat_row")));
        seat.setCol(toInt(doc.getAttribute("seat_number")) - 1);
        seat.setSeatLabel(String.valueOf(doc.getAttribute("seat_label")));
        seat.setStatus("available");
        return seat;
    }

    // ---- Bulk insert khi tạo mới suất chiếu ----
    public void saveAll(List<ScreeningSeat> seats) {
        for (ScreeningSeat seat : seats) {
            String aql =
                "UPSERT { room_key: @roomKey, seat_row: @row, seat_number: @number } " +
                "INSERT { room_key: @roomKey, seat_row: @row, seat_number: @number, seat_label: @label } " +
                "UPDATE {} IN seats";
            Map<String, Object> bind = new HashMap<>();
            bind.put("roomKey", seat.getScreeningId());
            bind.put("row", seat.getRow());
            bind.put("number", seat.getCol() + 1);
            bind.put("label", seat.getSeatLabel());
            db.query(aql, bind, null, Void.class);
        }
    }

    // ---- Đếm ghế còn trống của một suất chiếu ----
    public long countAvailable(String screeningId) {
        String aql =
            "LET screening = DOCUMENT('screenings', @sid) " +
            "FOR s IN seats " +
            "FILTER s.room_key == screening.room_key " +
            "LET occupied = LENGTH(" +
            "  FOR e IN booking_seats " +
            "  FILTER e._to == s._id AND e.screening_key == @sid AND (" +
            "    e.booking_status == 'confirmed' OR " +
            "    (e.booking_status == 'holding' AND e.hold_expires_at != null AND DATE_TIMESTAMP(e.hold_expires_at) > DATE_NOW())" +
            "  ) " +
            "  LIMIT 1 RETURN 1" +
            ") " +
            "FILTER occupied == 0 " +
            "COLLECT WITH COUNT INTO total RETURN total";
        Map<String, Object> bind = new HashMap<>();
        bind.put("sid", screeningId);
        ArangoCursor<Long> cursor = db.query(aql, bind, null, Long.class);
        List<Long> result = cursor.asListRemaining();
        return result.isEmpty() ? 0 : result.get(0);
    }

    public boolean isSeatBookedForScreeningWithTx(String seatKey, String screeningId, String txId) {
        String aql =
            "LET seat = DOCUMENT('seats', @seatKey) " +
            "RETURN LENGTH(" +
            "  FOR e IN booking_seats " +
            "  FILTER e._to == seat._id AND e.screening_key == @sid AND (" +
            "    e.booking_status == 'confirmed' OR " +
            "    (e.booking_status == 'holding' AND e.hold_expires_at != null AND DATE_TIMESTAMP(e.hold_expires_at) > DATE_NOW())" +
            "  ) " +
            "  LIMIT 1 RETURN 1" +
            ") > 0";
        Map<String, Object> bind = new HashMap<>();
        bind.put("seatKey", seatKey);
        bind.put("sid", screeningId);
        ArangoCursor<Boolean> cursor = db.query(
                aql,
                bind,
                new com.arangodb.model.AqlQueryOptions().streamTransactionId(txId),
                Boolean.class
        );
        return cursor.hasNext() && Boolean.TRUE.equals(cursor.next());
    }

    public boolean seatBelongsToRoomWithTx(String seatKey, String roomKey, String txId) {
        String aql =
            "LET seat = DOCUMENT('seats', @seatKey) " +
            "RETURN seat != null && seat.room_key == @roomKey";
        Map<String, Object> bind = new HashMap<>();
        bind.put("seatKey", seatKey);
        bind.put("roomKey", roomKey);
        ArangoCursor<Boolean> cursor = db.query(
                aql,
                bind,
                new com.arangodb.model.AqlQueryOptions().streamTransactionId(txId),
                Boolean.class
        );
        return cursor.hasNext() && Boolean.TRUE.equals(cursor.next());
    }

    public long countByRoom(String roomKey) {
        String aql =
            "FOR s IN seats FILTER s.room_key == @roomKey COLLECT WITH COUNT INTO c RETURN c";
        ArangoCursor<Long> cursor = db.query(aql, Map.of("roomKey", roomKey), null, Long.class);
        List<Long> result = cursor.asListRemaining();
        return result.isEmpty() ? 0 : result.get(0);
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }
}
