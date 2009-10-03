package fr.turtlesport.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import fr.turtlesport.geo.IGeoPositionWithAlt;
import fr.turtlesport.log.TurtleLogger;
import fr.turtlesport.protocol.data.D304TrkPointType;
import fr.turtlesport.util.GeoUtil;

/**
 * @author Denis Apparicio
 * 
 */
public final class RunTrkTableManager extends AbstractTableManager {
  private static TurtleLogger       log;
  static {
    log = (TurtleLogger) TurtleLogger.getLogger(RunTrkTableManager.class);
  }

  private static RunTrkTableManager singleton = new RunTrkTableManager();

  /**
   * 
   */
  private RunTrkTableManager() {
    super();
  }

  /**
   * Restitue une instance unique.
   */
  public static RunTrkTableManager getInstance() {
    return singleton;
  }

  /*
   * (non-Javadoc)
   * 
   * @see fr.turtlesport.db.AbstractTableManager#getTableName()
   */
  @Override
  public String getTableName() {
    return DatabaseManager.TABLE_RUN_TRK;
  }

  /**
   * 
   * Insertion d'un point.
   * 
   * @param id
   * @param trk
   * @throws SQLException
   */
  public void store(int id, D304TrkPointType trk) throws SQLException {
    log.debug(">>store id=" + id);

    if (trk == null) {
      throw new IllegalArgumentException("trk est null");
    }

    store(id,
          trk.getPosn().getLatitude(),
          trk.getPosn().getLongitude(),
          trk.getTime(),
          trk.getAltitude(),
          trk.getDistance(),
          trk.getHeartRate(),
          trk.getCadence());

    log.debug("<<store");
  }

  /**
   * 
   * Insertion d'un point.
   * 
   * @param id
   * @param trk
   * @throws SQLException
   */
  public void store(int id, IGeoPositionWithAlt trk) throws SQLException {
    log.debug(">>store id=" + id);

    if (trk == null) {
      throw new IllegalArgumentException("trk est null");
    }

    if (log.isInfoEnabled()) {
      log.info("store trk=" + trk.toString() + trk.isValidElevation());
    }

    int latitude = GeoUtil.makeLatitudeFromGeo(trk.getLatitude());
    int longitude = GeoUtil.makeLatitudeFromGeo(trk.getLongitude());

    store(id,
          latitude,
          longitude,
          trk.getDate(),
          trk.isValidElevation() ? (float) trk.getElevation()
              : D304TrkPointType.INVALID_ALT,
          trk.isValidDistance() ? (float) trk.getDistanceMeters()
              : D304TrkPointType.INVALID_DISTANCE,
          trk.getHeartRate(),
          trk.getCadence());

    log.debug("<<store");
  }

  /**
   * Insertion d'un point.
   * 
   * @param id
   * @param lapIndex
   * @param latitude
   * @param longitude
   * @param time
   * @param altitude
   * @param distance
   * @param heartRate
   * @param cadence
   * @return
   * @throws SQLException
   */
  protected int store(int id,
                      int latitude,
                      int longitude,
                      Date time,
                      float altitude,
                      float distance,
                      int heartRate,
                      int cadence) throws SQLException {
    log.debug(">>store");

    boolean isInTransaction = DatabaseManager.isInTransaction();
    if (!isInTransaction) {
      DatabaseManager.beginTransaction();
    }

    Connection conn = DatabaseManager.getConnection();

    try {
      if (!RunTableManager.getInstance().exist(id)) {
        throw new SQLException("id=" + id + " non trouve.");
      }

      StringBuilder st = new StringBuilder();
      st.append("INSERT INTO ");
      st.append(getTableName());
      st.append(" VALUES(?, ?, ?, ?, ?, ?, ?, ?)");

      PreparedStatement pstmt = conn.prepareStatement(st.toString());

      pstmt.setInt(1, id);
      pstmt.setInt(2, latitude);
      pstmt.setInt(3, longitude);
      pstmt.setTimestamp(4, new Timestamp(time.getTime()));
      pstmt.setFloat(5, altitude);
      pstmt.setFloat(6, distance);
      pstmt.setInt(7, heartRate);
      pstmt.setInt(8, cadence);
      pstmt.executeUpdate();
    }
    catch (SQLException e) {
      if (!isInTransaction) {
        DatabaseManager.rollbackTransaction();
      }
      DatabaseManager.releaseConnection(conn);
      throw e;
    }

    // ok
    if (!isInTransaction) {
      DatabaseManager.commitTransaction();
    }
    DatabaseManager.releaseConnection(conn);

    log.debug("<<store id=" + id);
    return id;
  }

  /**
   * Suppresion de tout les points d'un run.
   * @param id id du run
   * @throws SQLException
   */
  protected void delete(int id) throws SQLException {
    log.debug(">>delete id=" + id);

    Connection conn = DatabaseManager.getConnection();

    try {
      StringBuilder st = new StringBuilder();
      st.append("DELETE FROM ");
      st.append(getTableName());
      st.append(" WHERE id = ?");

      PreparedStatement pstmt = conn.prepareStatement(st.toString());
      pstmt.setInt(1, id);
      pstmt.executeUpdate();
    }
    finally {
      DatabaseManager.releaseConnection(conn);
    }

    log.debug("<<delete id=" + id);
  }

  /**
   * Restitue les points valides.
   * 
   * @param idRun
   * @return
   * @throws SQLException
   */
  public DataRunTrk[] getTrks(int idRun) throws SQLException {
    if (log.isInfoEnabled()) {
      log.info(">>getTrks idRun=" + idRun);
    }
    DataRunTrk[] res = null;

    long startTime = System.currentTimeMillis();
    Connection conn = DatabaseManager.getConnection();
    try {
      StringBuilder st = new StringBuilder();
      st.append("SELECT * FROM ");
      st.append(getTableName());
      st.append(" WHERE id=?");
      st.append(" AND distance <> ?");
      st.append(" AND (latitude <> ? AND longitude <> ?) ");
      st.append(" ORDER BY distance");

      PreparedStatement pstmt = conn.prepareStatement(st.toString());
      pstmt.setInt(1, idRun);
      pstmt.setFloat(2, 1.0e25f);
      pstmt.setInt(3, 0x7FFFFFFF);
      pstmt.setInt(4, 0x7FFFFFFF);

      ArrayList<DataRunTrk> list = new ArrayList<DataRunTrk>();
      DataRunTrk trk;
      ResultSet rs = pstmt.executeQuery();
      while (rs.next()) {
        trk = new DataRunTrk();

        trk.setId(rs.getInt(1));
        trk.setLatitude(rs.getInt(2));
        trk.setLongitude(rs.getInt(3));
        trk.setTime(rs.getTimestamp(4));
        trk.setAltitude(rs.getFloat(5));
        trk.setDistance(rs.getFloat(6));
        trk.setHeartRate(rs.getInt(7));
        list.add(trk);
      }

      res = new DataRunTrk[list.size()];
      if (res.length > 0) {
        list.toArray(res);
      }
    }
    finally {
      DatabaseManager.releaseConnection(conn);
    }

    if (log.isInfoEnabled()) {
      long delay = System.currentTimeMillis() - startTime;
      log.info("<<getTrks delay=" + delay + "ms");
    }
    return res;
  }

  /**
   * Restitue les points valides d'une course entre deux dates.
   * 
   * @param idRun
   * @param date1
   * @param date2
   * @return
   * @throws SQLException
   */
  public DataRunTrk[] getTrks(int idRun, Date date1, Date date2) throws SQLException {
    if (log.isDebugEnabled()) {
      SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      log.debug(">>getTrks idRun=" + idRun + " date1=" + df.format(date1)
                + " date2=" + df.format(date2));
    }

    if (date1 == null) {
      throw new IllegalArgumentException("date1");
    }
    if (date2 == null) {
      throw new IllegalArgumentException("date2");
    }

    Date dateFirst;
    Date dateEnd;
    if (date1.after(date2)) {
      dateFirst = date2;
      dateEnd = date1;
    }
    else {
      dateFirst = date1;
      dateEnd = date2;
    }

    DataRunTrk[] res = null;

    Connection conn = DatabaseManager.getConnection();
    try {
      StringBuilder st = new StringBuilder();
      st.append("SELECT * FROM ");
      st.append(getTableName());
      st.append(" WHERE id=?");
      st.append(" AND distance <> ?");
      st.append(" AND (latitude <> ? AND longitude <> ?) ");
      st.append(" AND (time BETWEEN ? AND ?)");
      st.append(" ORDER BY distance");

      PreparedStatement pstmt = conn.prepareStatement(st.toString());
      pstmt.setInt(1, idRun);
      pstmt.setFloat(2, (float) 1.0e25);
      pstmt.setInt(3, 0x7FFFFFFF);
      pstmt.setInt(4, 0x7FFFFFFF);
      pstmt.setTimestamp(5, new Timestamp(dateFirst.getTime()));
      pstmt.setTimestamp(6, new Timestamp(dateEnd.getTime()));

      ArrayList<DataRunTrk> list = new ArrayList<DataRunTrk>();
      DataRunTrk trk;
      ResultSet rs = pstmt.executeQuery();
      while (rs.next()) {
        trk = new DataRunTrk();

        trk.setId(rs.getInt(1));
        trk.setLatitude(rs.getInt(2));
        trk.setLongitude(rs.getInt(3));
        trk.setTime(rs.getTimestamp(4));
        trk.setAltitude(rs.getInt(5));
        trk.setDistance(rs.getInt(6));
        trk.setHeartRate(rs.getInt(7));
        list.add(trk);

        if (log.isDebugEnabled()) {
          log.debug(trk);
        }
      }

      res = new DataRunTrk[list.size()];
      if (res.length > 0) {
        list.toArray(res);
      }
    }
    finally {
      DatabaseManager.releaseConnection(conn);
    }

    log.debug("<<getTrks");
    return res;
  }

  /**
   * Restitue la frequence cardiaque min d'un run.
   * 
   * @param idRun
   * @return
   * @throws SQLException
   */
  public int heartMin(int idRun) throws SQLException {
    log.debug(">>heartMin idRun=" + idRun);

    int res = 0;

    Connection conn = DatabaseManager.getConnection();
    try {
      StringBuilder st = new StringBuilder();
      st.append(" SELECT heart_rate FROM ");
      st.append(getTableName());
      st.append(" WHERE id=? AND heart_rate > 40");

      PreparedStatement pstmt = conn.prepareStatement(st.toString());
      pstmt.setInt(1, idRun);

      ResultSet rs = pstmt.executeQuery();
      if (rs.next()) {
        res = rs.getInt(1);
      }
    }
    finally {
      DatabaseManager.releaseConnection(conn);
    }

    log.debug("<<heartMin");
    return res;
  }

  /**
   * Restitue les denivel&eacute;s + rt -.
   * 
   * @param idRun
   * @return
   * @throws SQLException
   */
  public int[] altitude(int idRun) throws SQLException {
    log.debug(">>altitude idRun=" + idRun);

    int[] res;

    Connection conn = DatabaseManager.getConnection();
    try {
      StringBuilder st = new StringBuilder();
      st.append("SELECT altitude FROM ");
      st.append(getTableName());
      st.append(" WHERE id=?");
      st.append(" AND distance <> ?");
      st.append(" AND altitude <> ?");
      st.append(" AND (latitude <> ? AND longitude <> ?) ");
      st.append(" ORDER BY distance");

      PreparedStatement pstmt = conn.prepareStatement(st.toString());
      pstmt.setInt(1, idRun);
      pstmt.setFloat(2, (float) 1.0e25);
      pstmt.setFloat(3, (float) 1.0e25);
      pstmt.setInt(4, 0x7FFFFFFF);
      pstmt.setInt(5, 0x7FFFFFFF);

      ResultSet rs = pstmt.executeQuery();
      res = computeAltitude(rs);
    }
    finally {
      DatabaseManager.releaseConnection(conn);
    }

    log.debug("<<altitude");
    return res;
  }

  /**
   * Restitue les denivel&eacute;s + rt - entre deux dates.
   * 
   * @param date1
   * @param date2
   * 
   * @return
   * @throws SQLException
   */
  public int[] altitude(int idRun, Date date1, Date date2) throws SQLException {
    log.debug(">>altitude idRun=" + idRun);
    Date dateFirst;
    Date dateEnd;
    int[] res;

    if (date1 == null) {
      throw new IllegalArgumentException("date1");
    }
    if (date2 == null) {
      throw new IllegalArgumentException("date2");
    }

    if (date1.after(date2)) {
      dateFirst = date2;
      dateEnd = date1;
    }
    else {
      dateFirst = date1;
      dateEnd = date2;
    }

    Connection conn = DatabaseManager.getConnection();
    try {
      StringBuilder st = new StringBuilder();
      st.append("SELECT altitude FROM ");
      st.append(getTableName());
      st.append(" WHERE id=?");
      st.append(" AND (time BETWEEN ? AND ?)");
      st.append(" AND distance <> ?");
      st.append(" AND altitude <> ?");
      st.append(" AND (latitude <> ? AND longitude <> ?) ");
      st.append(" ORDER BY distance");

      PreparedStatement pstmt = conn.prepareStatement(st.toString());
      pstmt.setInt(1, idRun);
      pstmt.setTimestamp(2, new Timestamp(dateFirst.getTime()));
      pstmt.setTimestamp(3, new Timestamp(dateEnd.getTime()));
      pstmt.setFloat(4, (float) 1.0e25);
      pstmt.setFloat(5, (float) 1.0e25);
      pstmt.setInt(6, 0x7FFFFFFF);
      pstmt.setInt(7, 0x7FFFFFFF);

      ResultSet rs = pstmt.executeQuery();
      res = computeAltitude(rs);
    }
    finally {
      DatabaseManager.releaseConnection(conn);
    }

    log.debug("<<altitude");
    return res;
  }

  private int[] computeAltitude(ResultSet rs) throws SQLException {
    int[] res = new int[2];

    float altPlus = 0, altMoins = 0;
    float alt, cur = -1, tmp;
    if (rs.next()) {
      cur = rs.getFloat(1);
    }

    while (rs.next()) {
      alt = rs.getFloat(1);
      tmp = alt - cur;
      if (tmp > 0 && tmp < 8) {
        altPlus += tmp;
        cur = alt;
      }
      else if (tmp < 0 && tmp > -8) {
        altMoins -= tmp;
        cur = alt;
      }
    }

    res[0] = (int) altPlus;
    res[1] = (int) altMoins;
    return res;
  }

}