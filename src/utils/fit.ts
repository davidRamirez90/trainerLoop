import type { TelemetrySample } from '../types';

const FIT_EPOCH_MS = Date.UTC(1989, 11, 31, 0, 0, 0);
const FIT_PROTOCOL_VERSION = 0x10;
const FIT_PROFILE_VERSION = 0x0100;
const FIT_HEADER_SIZE = 14;

const BASE_TYPES = {
  enum: 0x00,
  uint8: 0x02,
  uint16: 0x84,
  uint32: 0x86,
} as const;

const INVALID_UINT8 = 0xff;
const INVALID_UINT16 = 0xffff;
const INVALID_UINT32 = 0xffffffff;

type FitField = {
  num: number;
  size: number;
  baseType: number;
};

type FitExportInput = {
  startTimeMs: number;
  elapsedSec: number;
  timerSec?: number;
  samples: TelemetrySample[];
  sport?: number;
};

const toFitTimestamp = (timestampMs: number) =>
  Math.max(0, Math.floor((timestampMs - FIT_EPOCH_MS) / 1000));

const clamp = (value: number, min: number, max: number) =>
  Math.min(max, Math.max(min, value));

const encodeUint16 = (value: number) => [
  value & 0xff,
  (value >> 8) & 0xff,
];

const encodeUint32 = (value: number) => [
  value & 0xff,
  (value >> 8) & 0xff,
  (value >> 16) & 0xff,
  (value >> 24) & 0xff,
];

const encodeValue = (field: FitField, value: number | null) => {
  const { baseType, size } = field;
  if (value === null || value === undefined || !Number.isFinite(value)) {
    if (baseType === BASE_TYPES.uint16) {
      return encodeUint16(INVALID_UINT16);
    }
    if (baseType === BASE_TYPES.uint32) {
      return encodeUint32(INVALID_UINT32);
    }
    return [INVALID_UINT8];
  }

  if (baseType === BASE_TYPES.uint16) {
    const next = clamp(Math.round(value), 0, INVALID_UINT16);
    return encodeUint16(next);
  }
  if (baseType === BASE_TYPES.uint32) {
    const next = clamp(Math.round(value), 0, INVALID_UINT32);
    return encodeUint32(next);
  }
  const next = clamp(Math.round(value), 0, INVALID_UINT8);
  return size === 1 ? [next] : Array(size).fill(next);
};

const buildDefinitionMessage = (
  localType: number,
  globalMessageNumber: number,
  fields: FitField[]
) => {
  const bytes: number[] = [];
  bytes.push(0x40 | (localType & 0x0f));
  bytes.push(0x00);
  bytes.push(0x00);
  bytes.push(globalMessageNumber & 0xff);
  bytes.push((globalMessageNumber >> 8) & 0xff);
  bytes.push(fields.length & 0xff);
  fields.forEach((field) => {
    bytes.push(field.num & 0xff);
    bytes.push(field.size & 0xff);
    bytes.push(field.baseType & 0xff);
  });
  return bytes;
};

const buildDataMessage = (
  localType: number,
  fields: FitField[],
  values: Array<number | null>
) => {
  const bytes: number[] = [];
  bytes.push(localType & 0x0f);
  fields.forEach((field, index) => {
    bytes.push(...encodeValue(field, values[index]));
  });
  return bytes;
};

const CRC_TABLE = [
  0x0000,
  0xcc01,
  0xd801,
  0x1400,
  0xf001,
  0x3c00,
  0x2800,
  0xe401,
  0xa001,
  0x6c00,
  0x7800,
  0xb401,
  0x5000,
  0x9c01,
  0x8801,
  0x4400,
];

const crc16 = (bytes: number[]) => {
  let crc = 0;
  bytes.forEach((byte) => {
    const value = byte & 0xff;
    let tmp = CRC_TABLE[crc & 0x0f];
    crc = (crc >> 4) & 0x0fff;
    crc ^= tmp ^ CRC_TABLE[value & 0x0f];
    tmp = CRC_TABLE[crc & 0x0f];
    crc = (crc >> 4) & 0x0fff;
    crc ^= tmp ^ CRC_TABLE[(value >> 4) & 0x0f];
  });
  return crc & 0xffff;
};

const normalizeSamples = (samples: TelemetrySample[]) => {
  const normalized: TelemetrySample[] = [];
  samples.forEach((sample) => {
    const timeSec = Math.max(0, Math.round(sample.timeSec));
    if (!Number.isFinite(timeSec)) {
      return;
    }
    const nextSample = { ...sample, timeSec };
    const last = normalized[normalized.length - 1];
    if (last && last.timeSec === timeSec) {
      normalized[normalized.length - 1] = nextSample;
      return;
    }
    if (!last || timeSec > last.timeSec) {
      normalized.push(nextSample);
    }
  });
  return normalized;
};

const computeAverage = (
  samples: TelemetrySample[],
  selector: (sample: TelemetrySample) => number,
  include: (value: number) => boolean
) => {
  let sum = 0;
  let count = 0;
  samples.forEach((sample) => {
    const value = selector(sample);
    if (!include(value)) {
      return;
    }
    sum += value;
    count += 1;
  });
  if (count === 0) {
    return null;
  }
  return sum / count;
};

const computeMax = (
  samples: TelemetrySample[],
  selector: (sample: TelemetrySample) => number,
  include: (value: number) => boolean
) => {
  let maxValue: number | null = null;
  samples.forEach((sample) => {
    const value = selector(sample);
    if (!include(value)) {
      return;
    }
    if (maxValue === null || value > maxValue) {
      maxValue = value;
    }
  });
  return maxValue;
};

export const buildFitFile = ({
  startTimeMs,
  elapsedSec,
  timerSec: timerSecOverride,
  samples,
  sport = 2,
}: FitExportInput) => {
  const normalizedSamples = normalizeSamples(samples);
  const fitStartTimestamp = toFitTimestamp(startTimeMs);
  const lastSampleSec =
    normalizedSamples.length > 0
      ? normalizedSamples[normalizedSamples.length - 1].timeSec
      : 0;
  const timerSec = Math.max(0, timerSecOverride ?? lastSampleSec);
  const totalElapsedSec = Math.max(elapsedSec, timerSec);
  const fitEndTimestamp = fitStartTimestamp + Math.round(totalElapsedSec);
  const totalElapsedMs = Math.round(totalElapsedSec * 1000);
  const totalTimerMs = Math.round(timerSec * 1000);

  const avgPower = computeAverage(
    normalizedSamples,
    (sample) => sample.powerWatts,
    () => true
  );
  const avgCadence = computeAverage(
    normalizedSamples,
    (sample) => sample.cadenceRpm,
    (value) => value > 0
  );
  const avgHr = computeAverage(
    normalizedSamples,
    (sample) => sample.hrBpm,
    (value) => value > 0
  );
  const maxPower = computeMax(
    normalizedSamples,
    (sample) => sample.powerWatts,
    () => true
  );
  const maxHr = computeMax(
    normalizedSamples,
    (sample) => sample.hrBpm,
    (value) => value > 0
  );

  const fileIdFields: FitField[] = [
    { num: 0, size: 1, baseType: BASE_TYPES.enum },
    { num: 1, size: 2, baseType: BASE_TYPES.uint16 },
    { num: 2, size: 2, baseType: BASE_TYPES.uint16 },
    { num: 4, size: 4, baseType: BASE_TYPES.uint32 },
  ];
  const fileCreatorFields: FitField[] = [
    { num: 0, size: 2, baseType: BASE_TYPES.uint16 },
    { num: 1, size: 1, baseType: BASE_TYPES.uint8 },
  ];
  const recordFields: FitField[] = [
    { num: 253, size: 4, baseType: BASE_TYPES.uint32 },
    { num: 7, size: 2, baseType: BASE_TYPES.uint16 },
    { num: 4, size: 1, baseType: BASE_TYPES.uint8 },
    { num: 3, size: 1, baseType: BASE_TYPES.uint8 },
  ];
  const sessionFields: FitField[] = [
    { num: 253, size: 4, baseType: BASE_TYPES.uint32 },
    { num: 2, size: 4, baseType: BASE_TYPES.uint32 },
    { num: 5, size: 1, baseType: BASE_TYPES.enum },
    { num: 7, size: 4, baseType: BASE_TYPES.uint32 },
    { num: 8, size: 4, baseType: BASE_TYPES.uint32 },
    { num: 15, size: 1, baseType: BASE_TYPES.uint8 },
    { num: 16, size: 1, baseType: BASE_TYPES.uint8 },
    { num: 17, size: 1, baseType: BASE_TYPES.uint8 },
    { num: 18, size: 2, baseType: BASE_TYPES.uint16 },
    { num: 19, size: 2, baseType: BASE_TYPES.uint16 },
  ];
  const activityFields: FitField[] = [
    { num: 253, size: 4, baseType: BASE_TYPES.uint32 },
    { num: 0, size: 4, baseType: BASE_TYPES.uint32 },
    { num: 1, size: 2, baseType: BASE_TYPES.uint16 },
    { num: 2, size: 1, baseType: BASE_TYPES.enum },
  ];

  const dataBytes: number[] = [];

  dataBytes.push(...buildDefinitionMessage(0, 0, fileIdFields));
  dataBytes.push(
    ...buildDataMessage(0, fileIdFields, [
      4,
      1,
      0,
      fitStartTimestamp,
    ])
  );

  dataBytes.push(...buildDefinitionMessage(1, 49, fileCreatorFields));
  dataBytes.push(...buildDataMessage(1, fileCreatorFields, [1, 1]));

  dataBytes.push(...buildDefinitionMessage(2, 20, recordFields));
  normalizedSamples.forEach((sample) => {
    const cadence = sample.cadenceRpm > 0 ? sample.cadenceRpm : null;
    const hr = sample.hrBpm > 0 ? sample.hrBpm : null;
    dataBytes.push(
      ...buildDataMessage(2, recordFields, [
        fitStartTimestamp + sample.timeSec,
        sample.powerWatts,
        cadence,
        hr,
      ])
    );
  });

  dataBytes.push(...buildDefinitionMessage(3, 18, sessionFields));
  dataBytes.push(
    ...buildDataMessage(3, sessionFields, [
      fitEndTimestamp,
      fitStartTimestamp,
      sport,
      totalElapsedMs,
      totalTimerMs,
      avgCadence,
      avgHr,
      maxHr,
      avgPower,
      maxPower,
    ])
  );

  dataBytes.push(...buildDefinitionMessage(4, 34, activityFields));
  dataBytes.push(
    ...buildDataMessage(4, activityFields, [
      fitEndTimestamp,
      totalTimerMs,
      1,
      0,
    ])
  );

  const header = new Uint8Array(FIT_HEADER_SIZE);
  header[0] = FIT_HEADER_SIZE;
  header[1] = FIT_PROTOCOL_VERSION;
  header[2] = FIT_PROFILE_VERSION & 0xff;
  header[3] = (FIT_PROFILE_VERSION >> 8) & 0xff;
  const dataSize = dataBytes.length;
  header[4] = dataSize & 0xff;
  header[5] = (dataSize >> 8) & 0xff;
  header[6] = (dataSize >> 16) & 0xff;
  header[7] = (dataSize >> 24) & 0xff;
  header[8] = 0x2e;
  header[9] = 0x46;
  header[10] = 0x49;
  header[11] = 0x54;
  const headerCrc = crc16(Array.from(header.slice(0, 12)));
  header[12] = headerCrc & 0xff;
  header[13] = (headerCrc >> 8) & 0xff;

  const fileSize = header.length + dataBytes.length + 2;
  const fileBytes = new Uint8Array(fileSize);
  fileBytes.set(header, 0);
  fileBytes.set(dataBytes, header.length);
  const fileCrc = crc16(dataBytes);
  fileBytes[header.length + dataBytes.length] = fileCrc & 0xff;
  fileBytes[header.length + dataBytes.length + 1] = (fileCrc >> 8) & 0xff;

  return fileBytes;
};
