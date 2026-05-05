'use strict';

const db = require('@arangodb').db;
const internal = require('internal');
const aqlFunctions = require('@arangodb/aql/functions');

const targetDatabase = internal.env.ARANGO_DATABASE || 'cinema_db';

function ensureDatabase(name) {
  db._useDatabase('_system');
  if (name !== '_system' && !db._databases().includes(name)) {
    db._createDatabase(name);
    print('[arangodb-init] Created database: ' + name);
  }
  db._useDatabase(name);
}

function registerBookingTransactionProcedure() {
  const functionName = 'CINEMA::TX_CREATE_BOOKING';
  const functionBody = `function(payload) {
    var db = require('@arangodb').db;

    if (!payload || !payload.userId || !payload.screeningId || !payload.seatKeys || payload.seatKeys.length === 0) {
      throw new Error('Payload dat ve khong hop le');
    }

    var seatKeySet = {};
    for (var i = 0; i < payload.seatKeys.length; i++) {
      var key = payload.seatKeys[i];
      if (seatKeySet[key]) {
        throw new Error('Danh sach ghe bi trung: ' + key);
      }
      seatKeySet[key] = true;
    }

    return db._executeTransaction({
      collections: {
        read: ['screenings', 'movies', 'rooms', 'seats', 'users', 'booking_seats'],
        write: ['bookings', 'booking_seats', 'users', 'audit_logs']
      },
      params: payload,
      action: function(params) {
        var db = require('@arangodb').db;

        var screening;
        try {
          screening = db.screenings.document(params.screeningId);
        } catch (e) {
          throw new Error('Suat chieu khong ton tai');
        }
        if (!screening || screening.status !== 'active') {
          throw new Error('Suat chieu da ket thuc hoac bi huy');
        }

        var movie = null;
        if (screening.movie_key) {
          try {
            movie = db.movies.document(screening.movie_key);
          } catch (e) {
            movie = null;
          }
        }

        var room = null;
        if (screening.room_key) {
          try {
            room = db.rooms.document(screening.room_key);
          } catch (e) {
            room = null;
          }
        }

        var selectedSeats = [];
        for (var i = 0; i < params.seatKeys.length; i++) {
          var seatKey = params.seatKeys[i];

          var seat;
          try {
            seat = db.seats.document(seatKey);
          } catch (e) {
            throw new Error('Ghe khong ton tai: ' + seatKey);
          }

          if (seat.room_key !== screening.room_key) {
            throw new Error('Ghe khong thuoc phong chieu cua suat nay: ' + seatKey);
          }

          var expiredEdges = db._query(
            'FOR e IN booking_seats ' +
            'FILTER e._to == @seatId AND e.screening_key == @sid AND e.booking_status == "holding" ' +
            'FILTER e.hold_expires_at != null AND DATE_TIMESTAMP(e.hold_expires_at) <= DATE_NOW() ' +
            'RETURN { edgeKey: e._key, bookingKey: PARSE_IDENTIFIER(e._from).key }',
            { seatId: seat._id, sid: params.screeningId }
          ).toArray();
          for (var x = 0; x < expiredEdges.length; x++) {
            var item = expiredEdges[x];
            db.booking_seats.remove(item.edgeKey);
            db._query(
              'FOR b IN bookings FILTER b._key == @key AND b.status == "holding" ' +
              'UPDATE b WITH { status: "cancelled", cancelled_at: DATE_ISO8601(DATE_NOW()), cancel_reason: "hold_expired" } IN bookings',
              { key: item.bookingKey }
            );
          }

          var occupied = db._query(
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

        var user;
        try {
          user = db.users.document(params.userId);
        } catch (e) {
          throw new Error('Nguoi dung khong ton tai');
        }

        var totalAmount = Number(screening.price || 0) * selectedSeats.length;
        var bookingCode = params.bookingCode || ('BK' + Date.now());
        var createdAt = params.createdAt || new Date().toISOString();
        var holdExpiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString();

        var bookingDoc = {
          booking_code: bookingCode,
          user_key: params.userId,
          screening_key: params.screeningId,
          movie_key: screening.movie_key,
          seat_keys: params.seatKeys,
          seat_labels: selectedSeats.map(function(s) { return s.seat_label; }),
          total_amount: totalAmount,
          status: 'holding',
          created_at: createdAt,
          hold_expires_at: holdExpiresAt,
          movie_title: movie ? (movie.title || '') : '',
          show_time: screening.start_time || '',
          cinema_name: room ? (room.name || '') : ''
        };

        var bookingMeta = db.bookings.save(bookingDoc);

        for (var j = 0; j < params.seatKeys.length; j++) {
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
            seatLabels: selectedSeats.map(function(s) { return s.seat_label; }),
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
  }`;

  try {
    aqlFunctions.unregister(functionName);
  } catch (error) {
    // Ignore if function does not exist.
  }

  aqlFunctions.register(functionName, functionBody, false);
  print('[arangodb-init] Registered function: ' + functionName);
}

ensureDatabase(targetDatabase);
registerBookingTransactionProcedure();
