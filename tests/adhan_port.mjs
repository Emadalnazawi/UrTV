// A port of the arithmetic of adhan-js 4.4.6 (Batoul Apps, MIT licence, Copyright (c) 2016 Batoul Apps) written from reading its source, so
// that the prayer times of this project can be checked against a second, independent calculation: Jean Meeus' "Astronomical Algorithms"
// (apparent solar longitude, nutation, sidereal time, three-day interpolation of the transit and the hour angles), the way adhan does it,
// and not the short series (sun at 5, 6, 12, 13 and 18 o'clock) of PrayerMath.java and template.html.
//
// Nothing of adhan is executed or copied as a file; this is a re-write of its formulas (Astronomical.js, SolarCoordinates.js, SolarTime.js,
// PrayerTimes.js, DateUtils.js). tests/prayer_crosscheck_test.py first runs this port against the numbers adhan's own unit tests expect,
// so a slip in the port shows there and not as a false difference in a city.
//
// Left out because no city of the app is near it: the Moonsighting method, the polar-circle resolution, Hanafi/Shafi other than the
// shadow length, and the high-latitude rules other than «middle of the night» (which only acts when Fajr/Isha do not exist or fall into
// the night's middle; it never acts below about 48° north).
const RAD = Math.PI / 180, DEG = 180 / Math.PI;
const norm = (n, max) => n - max * Math.floor(n / max);
const unwind = a => norm(a, 360);
const quadrant = a => (a >= -180 && a <= 180) ? a : a - 360 * Math.round(a / 360);
const sin = d => Math.sin(d * RAD), cos = d => Math.cos(d * RAD), tan = d => Math.tan(d * RAD);

function julianDay(y, mo, d, hours = 0) {
  const Y = Math.trunc(mo > 2 ? y : y - 1), M = Math.trunc(mo > 2 ? mo : mo + 12), D = d + hours / 24;
  const A = Math.trunc(Y / 100), B = Math.trunc(2 - A + Math.trunc(A / 4));
  return Math.trunc(365.25 * (Y + 4716)) + Math.trunc(30.6001 * (M + 1)) + D + B - 1524.5;
}

function solarCoordinates(jd) {
  const T = (jd - 2451545.0) / 36525;
  const L0 = unwind(280.4664567 + 36000.76983 * T + 0.0003032 * T * T);                       // mean longitude of the sun
  const Lp = unwind(218.3165 + 481267.8813 * T);                                              // of the moon
  const Om = unwind(125.04452 - 1934.136261 * T + 0.0020708 * T * T + T * T * T / 450000);    // ascending node
  const M = unwind(357.52911 + 35999.05029 * T - 0.0001537 * T * T);                          // mean anomaly
  const C = (1.914602 - 0.004817 * T - 0.000014 * T * T) * sin(M) + (0.019993 - 0.000101 * T) * sin(2 * M) + 0.000289 * sin(3 * M);
  const lambda = unwind(L0 + C - 0.00569 - 0.00478 * sin(125.04 - 1934.136 * T));             // apparent longitude
  const theta0 = unwind(280.46061837 + 360.98564736629 * (T * 36525) + 0.000387933 * T * T - T * T * T / 38710000);   // mean sidereal time
  const dPsi = -17.2 / 3600 * sin(Om) - 1.32 / 3600 * sin(2 * L0) - 0.23 / 3600 * sin(2 * Lp) + 0.21 / 3600 * sin(2 * Om);
  const dEps = 9.2 / 3600 * cos(Om) + 0.57 / 3600 * cos(2 * L0) + 0.1 / 3600 * cos(2 * Lp) - 0.09 / 3600 * cos(2 * Om);
  const eps0 = 23.439291 - 0.013004167 * T - 0.0000001639 * T * T + 0.0000005036 * T * T * T;
  const epsApp = eps0 + 0.00256 * cos(125.04 - 1934.136 * T);
  return {
    declination: Math.asin(sin(epsApp) * sin(lambda)) * DEG,
    rightAscension: unwind(Math.atan2(cos(epsApp) * sin(lambda), cos(lambda)) * DEG),
    siderealTime: theta0 + dPsi * 3600 * cos(eps0 + dEps) / 3600,
  };
}

const interpolate = (y2, y1, y3, n) => { const a = y2 - y1, b = y3 - y2; return y2 + n / 2 * (a + b + n * (b - a)); };
const interpolateAngles = (y2, y1, y3, n) => { const a = unwind(y2 - y1), b = unwind(y3 - y2); return y2 + n / 2 * (a + b + n * (b - a)); };

function solarTime(y, mo, d, lat, lon) {
  const jd = julianDay(y, mo, d, 0), s = solarCoordinates(jd), p = solarCoordinates(jd - 1), n = solarCoordinates(jd + 1);
  const Lw = -lon;
  let m0 = norm((s.rightAscension + Lw - s.siderealTime) / 360, 1);
  const expected = norm((12 - lon / 15) / 24, 1);   // near the date line m0 can be a day off
  if (m0 - expected > 0.5) m0 -= 1; else if (expected - m0 > 0.5) m0 += 1;
  const transit = (() => {
    const theta = unwind(s.siderealTime + 360.985647 * m0), a = unwind(interpolateAngles(s.rightAscension, p.rightAscension, n.rightAscension, m0));
    return (m0 + quadrant(theta - Lw - a) / -360) * 24;
  })();
  const hourAngle = (angle, after) => {
    const H0 = Math.acos((sin(angle) - sin(lat) * sin(s.declination)) / (cos(lat) * cos(s.declination))) * DEG;
    const m = after ? m0 + H0 / 360 : m0 - H0 / 360;
    const theta = unwind(s.siderealTime + 360.985647 * m), a = unwind(interpolateAngles(s.rightAscension, p.rightAscension, n.rightAscension, m));
    const delta = interpolate(s.declination, p.declination, n.declination, m), H = theta - Lw - a;
    const h = Math.asin(sin(lat) * sin(delta) + cos(lat) * cos(delta) * cos(H)) * DEG;
    return (m + (h - angle) / (360 * cos(delta) * cos(lat) * sin(H))) * 24;
  };
  const afternoon = shadow => hourAngle(Math.atan(1 / (shadow + tan(Math.abs(lat - s.declination)))) * DEG, true);
  return { transit, sunrise: hourAngle(-50 / 60, false), sunset: hourAngle(-50 / 60, true), hourAngle, afternoon };
}

// hours of the day (UTC, may be <0 or >24) -> the instant, truncated to the second the way adhan's TimeComponents does
function utcDate(h, y, mo, d) {
  const hh = Math.floor(h), mm = Math.floor((h - hh) * 60), ss = Math.floor((h - (hh + mm / 60)) * 3600);
  return Date.UTC(y, mo - 1, d, hh, mm, ss);
}
const addMin = (t, m) => t + m * 60000;
let rounding = true;   // the self-test of the sun series wants the unrounded instants: prayerTimes(..., {noRound: true})
const round = t => { if (!rounding) return t; const s = new Date(t).getUTCSeconds(); return t + (s >= 30 ? 60 - s : -s) * 1000; };

// params: {fajr, isha, ishaInterval, maghribAngle, shadow (1 = Shafi'i), adj: {fajr,sunrise,dhuhr,asr,maghrib,isha}}
// returns {fajr, sunrise, dhuhr, asr, maghrib, isha} as epoch milliseconds, rounded to the minute
export function prayerTimes(lat, lon, y, mo, d, params) {
  rounding = !(params && params.noRound);
  const p = { shadow: 1, ishaInterval: 0, maghribAngle: 0, ...params, adj: { fajr: 0, sunrise: 0, dhuhr: 0, asr: 0, maghrib: 0, isha: 0, ...(params.adj || {}) } };
  const st = solarTime(y, mo, d, lat, lon), tm = new Date(Date.UTC(y, mo - 1, d + 1)), tst = solarTime(tm.getUTCFullYear(), tm.getUTCMonth() + 1, tm.getUTCDate(), lat, lon);
  const dhuhr = utcDate(st.transit, y, mo, d), sunrise = utcDate(st.sunrise, y, mo, d), sunset = utcDate(st.sunset, y, mo, d);
  const asr = utcDate(st.afternoon(p.shadow), y, mo, d);
  const night = (utcDate(tst.sunrise, tm.getUTCFullYear(), tm.getUTCMonth() + 1, tm.getUTCDate()) - sunset) / 1000;
  let fajr = utcDate(st.hourAngle(-p.fajr, false), y, mo, d);
  const safeFajr = sunrise - night / 2 * 1000;                 // «middle of the night»
  if (isNaN(fajr) || safeFajr > fajr) fajr = safeFajr;
  let isha;
  if (p.ishaInterval > 0) isha = addMin(sunset, p.ishaInterval);
  else {
    isha = utcDate(st.hourAngle(-p.isha, true), y, mo, d);
    const safeIsha = sunset + night / 2 * 1000;
    if (isNaN(isha) || safeIsha < isha) isha = safeIsha;
  }
  let maghrib = sunset;
  if (p.maghribAngle) {
    const byAngle = utcDate(st.hourAngle(-p.maghribAngle, true), y, mo, d);
    if (sunset < byAngle && isha > byAngle) maghrib = byAngle;
  }
  const a = p.adj;
  return {
    fajr: round(addMin(fajr, a.fajr)), sunrise: round(addMin(sunrise, a.sunrise)), dhuhr: round(addMin(dhuhr, a.dhuhr)),
    asr: round(addMin(asr, a.asr)), maghrib: round(addMin(maghrib, a.maghrib)), isha: round(addMin(isha, a.isha)),
  };
}

// The methods of adhan-js' CalculationMethod, as the numbers it has, plus the ones adhan has none for ("Other" with our numbers)
export const METHODS = {
  northAmerica: { fajr: 15, isha: 15, adj: { dhuhr: 1 } },   // adhan adds a minute to Dhuhr for this one, the Egyptian, the Muslim World League and Karachi
  egyptian: { fajr: 19.5, isha: 17.5, adj: { dhuhr: 1 } },
  ummAlQura: { fajr: 18.5, isha: 0, ishaInterval: 90 },
  dubai: { fajr: 18.2, isha: 18.2, adj: { sunrise: -3, dhuhr: 3, asr: 3, maghrib: 3 } },
  qatar: { fajr: 18, isha: 0, ishaInterval: 90 },
  kuwait: { fajr: 18, isha: 17.5 },
};
