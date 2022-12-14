-- Function to book tickets

CREATE OR REPLACE FUNCTION book_tickets (
  IN train_number  VARCHAR(5),
  IN depdate       DATE,
  IN preference    CHAR(2),
  IN names         TEXT[],
  OUT result       TEXT
) AS $$
  DECLARE

    arr_len INTEGER := array_length(names, 1);
    num_left INTEGER := 0;
    total_seats INTEGER := 0;
    start_seat INTEGER := 0;
    mod INTEGER := 0;
    pnr TEXT;
    val INTEGER;
    other_val INTEGER;
    seat_types INTEGER := 0;
    all_values TEXT := '';
    ac_seat_names TEXT[] := '{ "SU", "LB", "LB", "UB", "UB", "SL" }';
    sl_seat_names TEXT[] := '{ "SU", "LB", "MB", "UB", "LB", "MB", "UB", "SL" }';
    pref_seat_names TEXT[];
    pref_prefix TEXT;
  BEGIN
    IF preference = 'AC' THEN
      pref_seat_names := ac_seat_names;
      seat_types := 6;
    ELSE
      pref_seat_names := sl_seat_names;
      seat_types := 8;
    END IF;

    EXECUTE format(
    '
      SELECT '||preference||'_seats_left, '||preference||'_seats_total FROM %I 
      WHERE dep_date = %L;
    ', train_number || '-' || LOWER(preference), depdate
    ) INTO num_left, total_seats;
    
    start_seat := total_seats - num_left;

    IF num_left IS NULL THEN
      result := 'No such train: ' || train_number; 
      RAISE EXCEPTION 'No train: % on date: %', train_number, depdate;

    ELSIF num_left < arr_len THEN      
      result := 'Failed not enough seats'; 
      RAISE EXCEPTION 'Not enough seats left in train: %-% on: %', train_number, preference, depdate;

    ELSE 

      EXECUTE format('
        UPDATE %I SET %s_seats_left = %s_seats_left -%s 
        WHERE dep_date = %L;
      ', train_number || '-' || LOWER(preference), preference, preference, arr_len, depdate);

    END IF;
    
    IF preference = 'AC' THEN
      mod := 18;
    ELSE
      mod := 24;
    END IF;

    pref_prefix := LEFT(preference, 1);
    pnr := preference || train_number || '-' || depdate || '-' || start_seat+1;

    FOR ind IN 1..arr_len
    LOOP 
      val := start_seat+ind-1;
      other_val := val % mod + 1;
      IF all_values <> '' THEN all_values := all_values || ',' || E'\n';
      END IF;
      all_values := all_values || format (
        '(''%s'',''%s-%s'',%L, ''%s'')', 
        pnr,
        pref_prefix || (val / mod)+1, other_val, names[ind],
        pref_seat_names[(other_val) % seat_types + 1]
      );
    END LOOP;

    train_number := train_number || '-' || depdate;

    EXECUTE format(
      '
        INSERT INTO "%s" VALUES %s;
      ', train_number, all_values 
    );
    -- RAISE NOTICE '% \n %',names[1], names[2];
    -- RAISE NOTICE 'Booked Ticket, PNR: %', pnr;

    result := all_values;

    EXCEPTION 
	    WHEN undefined_table THEN 
        result := 'No such train'; 
	      RAISE EXCEPTION 'No such train: %', train_number;
        RETURN;


  END
$$ LANGUAGE plpgsql;
