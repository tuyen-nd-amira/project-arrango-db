'use strict';

const db = require('@arangodb').db;
const createRouter = require('@arangodb/foxx/router');

const router = createRouter();
module.context.use(router);

router.post('/create-booking', function (req, res) {
  const payload = req.body || {};

  if (!payload.userId || !payload.screeningId || !Array.isArray(payload.seatKeys) || payload.seatKeys.length === 0) {
    res.status(400);
    res.send({ error: 'Payload dat ve khong hop le' });
    return;
  }

  const seatKeySet = {};
  for (let i = 0; i < payload.seatKeys.length; i++) {
    const key = payload.seatKeys[i];
    if (seatKeySet[key]) {
      res.status(400);
      res.send({ error: 'Danh sach ghe bi trung: ' + key });
      return;
    }
    seatKeySet[key] = true;
  }

  try {
    const result = db._executeTransaction({
      collections: {
        read: ['screenings', 'movies', 'rooms', 'seats', 'users', 'booking_seats'],
        write: ['bookings', 'booking_seats', 'users', 'audit_logs']
      },
      params: payload,
      action: function (params) {
        const db = require('@arangodb').db;

        let screening;
        try {
          screening = db.screenings.document(params.screeningId);
        } catch (e) {
          throw new Error('Suat chieu khong ton tai');
        }
        if (!screening || screening.status !== 'active') {
          throw new Error('Suat chieu da ket thuc hoac bi huy');
        }

        let movie = null;
        if (screening.movie_key) {
          try {
            movie = db.movies.document(screening.movie_key);
          } catch (e) {
            movie = null;
          }
        }

        let room = null;
        if (screening.room_key) {
          try {
            room = db.rooms.document(screening.room_key);
          } catch (e) {
            room = null;
          }
        }

        const selectedSeats = [];
        for (let i = 0; i < params.seatKeys.length; i++) {
          const seatKey = params.seatKeys[i];

          let seat;
          try {
            seat = db.seats.document(seatKey);
          } catch (e) {
            throw new Error('Ghe khong ton tai: ' + seatKey);
          }

          if (seat.room_key !== screening.room_key) {
            throw new Error('Ghe khong thuoc phong chieu cua suat nay: ' + seatKey);
          }

          const occupied = db._query(
            'FOR e IN booking_seats FILTER e._to == @seatId AND e.screening_key == @sid AND e.booking_status == "confirmed" LIMIT 1 RETURN 1',
            { seatId: 'seats/' + seatKey, sid: params.screeningId }
          ).toArray().length > 0;

          if (occupied) {
            throw new Error('Ghe ' + seat.seat_label + ' da co nguoi dat');
          }

          selectedSeats.push(seat);
        }

        try {
          db.users.document(params.userId);
        } catch (e) {
          throw new Error('Nguoi dung khong ton tai');
        }

        const totalAmount = Number(screening.price || 0) * selectedSeats.length;
        const bookingCode = params.bookingCode || ('BK' + Date.now());
        const createdAt = params.createdAt || new Date().toISOString();

        const bookingDoc = {
          booking_code: bookingCode,
          user_key: params.userId,
          screening_key: params.screeningId,
          movie_key: screening.movie_key,
          seat_keys: params.seatKeys,
          seat_labels: selectedSeats.map(function (s) { return s.seat_label; }),
          total_amount: totalAmount,
          status: 'confirmed',
          created_at: createdAt,
          movie_title: movie ? (movie.title || '') : '',
          show_time: screening.start_time || '',
          cinema_name: room ? (room.name || '') : ''
        };

        const bookingMeta = db.bookings.save(bookingDoc);

        for (let j = 0; j < params.seatKeys.length; j++) {
          db.booking_seats.save({
            _from: 'bookings/' + bookingMeta._key,
            _to: 'seats/' + params.seatKeys[j],
            screening_key: params.screeningId,
            booking_status: 'confirmed',
            created_at: createdAt
          });
        }

        db._query(
          'FOR u IN users FILTER u._key == @uid UPDATE u WITH { totalSpent: TO_NUMBER(u.totalSpent) + @amount } IN users',
          { uid: params.userId, amount: totalAmount }
        );

        db.audit_logs.save({
          action: 'create_booking',
          entity_key: bookingMeta._key,
          payload: {
            bookingCode: bookingCode,
            userKey: params.userId,
            screeningKey: params.screeningId
          },
          created_at: createdAt
        });

        return {
          booking: {
            _key: bookingMeta._key,
            _id: bookingMeta._id,
            userId: params.userId,
            screeningId: params.screeningId,
            movieId: screening.movie_key,
            bookingCode: bookingCode,
            seatKeys: params.seatKeys,
            seatLabels: selectedSeats.map(function (s) { return s.seat_label; }),
            totalAmount: totalAmount,
            status: 'confirmed',
            createdAt: createdAt,
            movieTitle: movie ? (movie.title || '') : '',
            showTime: screening.start_time || '',
            cinemaName: room ? (room.name || '') : ''
          }
        };
      }
    });

    res.status(200);
    res.send(result);
  } catch (error) {
    res.status(400);
    res.send({ error: error.message || 'Dat ve that bai' });
  }
})
.body(['application/json'], 'Booking payload')
.response(['application/json'], 'Booking result');
