// The port of adhan's formulas (tests/adhan_port.mjs) against the values adhan's own unit tests expect (adhan-java, PrayerTimesTest.kt: testPrayerTimes
// and testEgyptian), so a slip in the port shows here and not as a false difference in a city of tests/prayer_crosscheck.tsv.
import { prayerTimes, METHODS } from './adhan_port.mjs';

let failed = 0;
const hm = (zone, t) => new Intl.DateTimeFormat('en-GB', { timeZone: zone, hourCycle: 'h23', hour: '2-digit', minute: '2-digit' }).format(t);
const row = (zone, r) => [r.fajr, r.sunrise, r.dhuhr, r.asr, r.maghrib, r.isha].map(t => hm(zone, t)).join(' ');
for (const [name, got, want] of [
  ['Raleigh 2015-07-12, ISNA, Hanafi', row('America/New_York', prayerTimes(35.7750, -78.6336, 2015, 7, 12, { ...METHODS.northAmerica, shadow: 2 })), '04:42 06:08 13:21 18:22 20:32 21:57'],
  ['Cairo 2020-01-01, Egyptian', row('Africa/Cairo', prayerTimes(30.028703, 31.249528, 2020, 1, 1, METHODS.egyptian)), '05:18 06:51 11:59 14:47 17:06 18:29'],
]) {
  const ok = got === want;
  console.log((ok ? 'PASS ' : 'FAIL ') + `adhan port: ${name} gives the six times adhan's own test expects` + (ok ? '' : `  -> ${got} vs ${want}`));
  if (!ok) failed++;
}
process.exit(failed ? 1 : 0);
