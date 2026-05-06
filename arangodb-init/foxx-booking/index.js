'use strict';

const db = require('@arangodb').db;
const createRouter = require('@arangodb/foxx/router');
const joi = require('joi');

const router = createRouter();
module.context.use(router);
const HOLD_MINUTES = 5;

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

          const expiredEdges = db._query(
            'FOR e IN booking_seats ' +
            'FILTER e._to == @seatId AND e.screening_key == @sid AND e.booking_status == "holding" ' +
            'FILTER e.hold_expires_at != null AND DATE_TIMESTAMP(e.hold_expires_at) <= DATE_NOW() ' +
            'RETURN { edgeKey: e._key, bookingKey: PARSE_IDENTIFIER(e._from).key }',
            { seatId: seat._id, sid: params.screeningId }
          ).toArray();

          for (let x = 0; x < expiredEdges.length; x++) {
            const item = expiredEdges[x];
            db.booking_seats.remove(item.edgeKey);
            db._query(
              'FOR b IN bookings FILTER b._key == @key AND b.status == "holding" ' +
              'UPDATE b WITH { status: "cancelled", cancelled_at: DATE_ISO8601(DATE_NOW()), cancel_reason: "hold_expired" } IN bookings',
              { key: item.bookingKey }
            );
          }

          const occupied = db._query(
            'FOR e IN booking_seats FILTER e._to == @seatId AND e.screening_key == @sid AND (' +
            '  e.booking_status == "confirmed" OR ' +
            '  (e.booking_status == "holding" AND e.hold_expires_at != null AND DATE_TIMESTAMP(e.hold_expires_at) > DATE_NOW())' +
            ') LIMIT 1 RETURN 1',
            { seatId: 'seats/' + seatKey, sid: params.screeningId }
          ).toArray().length > 0;

          if (occupied) {
            throw new Error('Ghe ' + seat.seat_label + ' da co nguoi dat');
          }

          selectedSeats.push(seat);
        }

        let user;
        try {
          user = db.users.document(params.userId);
        } catch (e) {
          throw new Error('Nguoi dung khong ton tai');
        }

        let totalAmount = Number(screening.price || 0) * selectedSeats.length;
        
        // Cập nhật Trigger tự động giảm giá
        if (user.role === 'premium') {
          totalAmount = totalAmount * 0.90; // Giảm 10%
        } else if (user.role === 'vip') {
          totalAmount = totalAmount * 0.95; // Giảm 5%
        }
        const bookingCode = params.bookingCode || ('BK' + Date.now());
        const createdAt = params.createdAt || new Date().toISOString();
        const holdExpiresAt = new Date(Date.now() + HOLD_MINUTES * 60 * 1000).toISOString();

        const bookingDoc = {
          booking_code: bookingCode,
          user_key: params.userId,
          screening_key: params.screeningId,
          movie_key: screening.movie_key,
          seat_keys: params.seatKeys,
          seat_labels: selectedSeats.map(function (s) { return s.seat_label; }),
          total_amount: totalAmount,
          status: 'holding',
          created_at: createdAt,
          hold_expires_at: holdExpiresAt,
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
            booking_status: 'holding',
            created_at: createdAt,
            hold_expires_at: holdExpiresAt
          });
        }

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
            status: 'holding',
            createdAt: createdAt,
            holdExpiresAt: holdExpiresAt,
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

router.post('/complete-payment', function (req, res) {
  const payload = req.body || {};

  if (!payload.bookingId || !payload.userId) {
    res.status(400);
    res.send({ error: 'Payload thanh toan khong hop le' });
    return;
  }

  try {
    const result = db._executeTransaction({
      collections: {
        read: ['bookings', 'users'],
        write: ['bookings', 'booking_seats', 'users', 'audit_logs']
      },
      params: payload,
      action: function (params) {
        const db = require('@arangodb').db;

        let booking;
        try {
          booking = db.bookings.document(params.bookingId);
        } catch (e) {
          throw new Error('Booking khong ton tai');
        }

        if (booking.user_key !== params.userId) {
          throw new Error('Khong co quyen thanh toan booking nay');
        }

        if (booking.status === 'confirmed') {
          return {
            booking: {
              _key: booking._key,
              _id: booking._id,
              userId: booking.user_key,
              screeningId: booking.screening_key,
              movieId: booking.movie_key,
              bookingCode: booking.booking_code,
              seatKeys: booking.seat_keys || [],
              seatLabels: booking.seat_labels || [],
              totalAmount: Number(booking.total_amount || 0),
              status: booking.status,
              createdAt: booking.created_at,
              holdExpiresAt: booking.hold_expires_at || null,
              movieTitle: booking.movie_title || '',
              showTime: booking.show_time || '',
              cinemaName: booking.cinema_name || ''
            }
          };
        }

        if (booking.status !== 'holding') {
          throw new Error('Booking khong o trang thai giu cho de thanh toan');
        }

        const nowTs = Date.now();
        const holdTs = booking.hold_expires_at ? Date.parse(booking.hold_expires_at) : 0;
        if (!holdTs || holdTs <= nowTs) {
          db._query(
            'FOR e IN booking_seats FILTER e._from == @fromId REMOVE e IN booking_seats',
            { fromId: booking._id }
          );
          db.bookings.update(booking._key, {
            status: 'cancelled',
            cancelled_at: new Date(nowTs).toISOString(),
            cancel_reason: 'hold_expired'
          });
          throw new Error('Giu cho da het han, vui long dat lai');
        }

        db.bookings.update(booking._key, {
          status: 'confirmed',
          confirmed_at: new Date(nowTs).toISOString(),
          hold_expires_at: null
        });

        db._query(
          'FOR e IN booking_seats FILTER e._from == @fromId ' +
          'UPDATE e WITH { booking_status: "confirmed", hold_expires_at: null, confirmed_at: DATE_ISO8601(DATE_NOW()) } IN booking_seats',
          { fromId: booking._id }
        );

        const updatedUser = db._query(
          'FOR u IN users FILTER u._key == @uid UPDATE u WITH { totalSpent: TO_NUMBER(u.totalSpent) + @amount } IN users RETURN NEW',
          { uid: params.userId, amount: Number(booking.total_amount || 0) }
        ).toArray()[0];

        // Trigger tự động cập nhật hạng thành viên
        if (updatedUser && updatedUser.role !== 'admin') {
          let newRole = 'normal';
          if (updatedUser.totalSpent >= 10000000) {
            newRole = 'premium';
          } else if (updatedUser.totalSpent >= 5000000) {
            newRole = 'vip';
          }
          if (updatedUser.role !== newRole) {
            db._query(
              'FOR u IN users FILTER u._key == @uid UPDATE u WITH { role: @newRole } IN users',
              { uid: params.userId, newRole: newRole }
            );
          }
        }

        db.audit_logs.save({
          action: 'complete_payment',
          entity_key: booking._key,
          payload: {
            bookingCode: booking.booking_code,
            userKey: params.userId,
            amount: Number(booking.total_amount || 0)
          },
          created_at: new Date(nowTs).toISOString()
        });

        return {
          booking: {
            _key: booking._key,
            _id: booking._id,
            userId: booking.user_key,
            screeningId: booking.screening_key,
            movieId: booking.movie_key,
            bookingCode: booking.booking_code,
            seatKeys: booking.seat_keys || [],
            seatLabels: booking.seat_labels || [],
            totalAmount: Number(booking.total_amount || 0),
            status: 'confirmed',
            createdAt: booking.created_at,
            holdExpiresAt: null,
            movieTitle: booking.movie_title || '',
            showTime: booking.show_time || '',
            cinemaName: booking.cinema_name || ''
          }
        };
      }
    });

    res.status(200);
    res.send(result);
  } catch (error) {
    res.status(400);
    res.send({ error: error.message || 'Thanh toan that bai' });
  }
})
.body(['application/json'], 'Payment payload')
.response(['application/json'], 'Payment result');

router.post('/cancel-holding', function (req, res) {
  const payload = req.body || {};

  if (!payload.bookingId || !payload.userId) {
    res.status(400);
    res.send({ error: 'Payload huy giu cho khong hop le' });
    return;
  }

  try {
    const result = db._executeTransaction({
      collections: {
        read: ['bookings'],
        write: ['bookings', 'booking_seats', 'audit_logs']
      },
      params: payload,
      action: function (params) {
        const db = require('@arangodb').db;

        let booking;
        try {
          booking = db.bookings.document(params.bookingId);
        } catch (e) {
          throw new Error('Booking khong ton tai');
        }

        if (booking.user_key !== params.userId) {
          throw new Error('Khong co quyen huy booking nay');
        }

        if (booking.status === 'confirmed') {
          throw new Error('Khong the huy booking da thanh toan');
        }

        if (booking.status === 'cancelled') {
          return {
            booking: {
              _key: booking._key,
              _id: booking._id,
              userId: booking.user_key,
              bookingCode: booking.booking_code,
              status: 'cancelled',
              movieTitle: booking.movie_title || '',
              showTime: booking.show_time || '',
              cinemaName: booking.cinema_name || '',
              seatLabels: booking.seat_labels || [],
              totalAmount: Number(booking.total_amount || 0),
              createdAt: booking.created_at,
              holdExpiresAt: null
            }
          };
        }

        const nowTs = Date.now();

        db._query(
          'FOR e IN booking_seats FILTER e._from == @fromId REMOVE e IN booking_seats',
          { fromId: booking._id }
        );

        db.bookings.update(booking._key, {
          status: 'cancelled',
          cancelled_at: new Date(nowTs).toISOString(),
          cancel_reason: 'user_cancelled',
          hold_expires_at: null
        });

        db.audit_logs.save({
          action: 'cancel_holding',
          entity_key: booking._key,
          payload: { bookingCode: booking.booking_code, userKey: params.userId },
          created_at: new Date(nowTs).toISOString()
        });

        return {
          booking: {
            _key: booking._key,
            _id: booking._id,
            userId: booking.user_key,
            bookingCode: booking.booking_code,
            status: 'cancelled',
            movieTitle: booking.movie_title || '',
            showTime: booking.show_time || '',
            cinemaName: booking.cinema_name || '',
            seatLabels: booking.seat_labels || [],
            totalAmount: Number(booking.total_amount || 0),
            createdAt: booking.created_at,
            holdExpiresAt: null
          }
        };
      }
    });

    res.status(200);
    res.send(result);
  } catch (error) {
    res.status(400);
    res.send({ error: error.message || 'Huy giu cho that bai' });
  }
})
.body(['application/json'], 'Cancel holding payload')
.response(['application/json'], 'Cancel result');

// -------------------------------------------------------------
// PROCEDURE: System Overview
// -------------------------------------------------------------
router.get('/reports/overview', function (req, res) {
  const data = db._query(`
    LET totalRevenue = (FOR b IN bookings FILTER b.status == 'confirmed' COLLECT AGGREGATE s = SUM(b.total_amount) RETURN s)[0]
    LET totalTickets = (FOR b IN bookings FILTER b.status == 'confirmed' COLLECT AGGREGATE s = SUM(LENGTH(b.seat_keys)) RETURN s)[0]
    LET totalBookings = (FOR b IN bookings FILTER b.status == 'confirmed' COLLECT WITH COUNT INTO c RETURN c)[0]
    LET totalUsers = (FOR u IN users COLLECT WITH COUNT INTO c RETURN c)[0]
    RETURN {
      totalRevenue: totalRevenue != null ? totalRevenue : 0,
      totalTickets: totalTickets != null ? totalTickets : 0,
      totalBookings: totalBookings != null ? totalBookings : 0,
      totalUsers: totalUsers != null ? totalUsers : 0
    }
  `).toArray()[0] || {};
  res.send(data);
})
.response(['application/json'], 'System Overview');

// -------------------------------------------------------------
// PROCEDURE: Movie Revenue Reports
// -------------------------------------------------------------
router.get('/reports/movies', function (req, res) {
  const data = db._query(`
    FOR m IN movies
      FILTER m.status == null OR m.status IN ['active', 'showing', 'coming_soon']
      LET revenueStats = (FOR b IN bookings FILTER b.movie_key == m._key AND b.status == 'confirmed' COLLECT AGGREGATE totalRev = SUM(b.total_amount), totalTkts = SUM(LENGTH(b.seat_keys)) RETURN { totalRevenue: totalRev, totalTickets: totalTkts })[0]
      LET stats = { totalRevenue: revenueStats != null && revenueStats.totalRevenue != null ? revenueStats.totalRevenue : 0, totalTickets: revenueStats != null && revenueStats.totalTickets != null ? revenueStats.totalTickets : 0 }
      LET totalBookings = (
        FOR b IN bookings
        FILTER b.movie_key == m._key AND b.status == 'confirmed'
        COLLECT WITH COUNT INTO c RETURN c
      )[0]
      SORT stats.totalRevenue DESC
      RETURN {
        movieId: m._key,
        movieTitle: m.title,
        totalRevenue: stats.totalRevenue,
        totalTickets: stats.totalTickets,
        totalBookings: totalBookings
      }
  `).toArray();
  res.send(data);
})
.response(['application/json'], 'Movie Revenue Stats');

router.get('/reports/movies/:movieId', function (req, res) {
  const movieId = req.pathParams.movieId;
  const data = db._query(`
    LET movie = DOCUMENT('movies', @mid)
    LET revenueStats = (FOR b IN bookings FILTER b.movie_key == @mid AND b.status == 'confirmed' COLLECT AGGREGATE totalRev = SUM(b.total_amount), totalTkts = SUM(LENGTH(b.seat_keys)) RETURN { totalRevenue: totalRev, totalTickets: totalTkts })[0]
    LET stats = { totalRevenue: revenueStats != null && revenueStats.totalRevenue != null ? revenueStats.totalRevenue : 0, totalTickets: revenueStats != null && revenueStats.totalTickets != null ? revenueStats.totalTickets : 0 }
    LET totalBookings = (
      FOR b IN bookings
      FILTER b.movie_key == @mid AND b.status == 'confirmed'
      COLLECT WITH COUNT INTO c RETURN c
    )[0]
    RETURN {
      movieId: @mid,
      movieTitle: movie == null ? '' : movie.title,
      totalRevenue: stats.totalRevenue,
      totalTickets: stats.totalTickets,
      totalBookings: totalBookings
    }
  `, { mid: movieId }).toArray()[0] || {};
  res.send(data);
})
.pathParam('movieId', joi.string().required(), 'Movie ID')
.response(['application/json'], 'Single Movie Revenue Stats');
