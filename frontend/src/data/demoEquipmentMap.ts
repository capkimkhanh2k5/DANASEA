/**
 * Authoritative equipment fixtures generated from DB_Demo/transactions/maintenance.json
 * and PostgreSQL vendor_equipment table.
 */

export interface DemoEquipmentRecord {
  id: string;
  vendorId: string;
  name: string;
  compatibilityGroup: string;
  capacity: number;
  status: "OPERATIONAL" | "MAINTENANCE" | "INSPECTION_DUE";
  lastMaintenance: string;
  nextInspection: string;
}

export const DEMO_EQUIPMENT_DATA: DemoEquipmentRecord[] = [
  // Kajin Vina (vender_2: baea9605-392b-5003-93f1-60f2c0582f65)
  {
    id: "e1010000-0000-0000-0000-000000000001",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Mô tô nước Sea-Doo Spark 90HP #01",
    compatibilityGroup: "JET_SKI",
    capacity: 2,
    status: "MAINTENANCE",
    lastMaintenance: "2026-11-05",
    nextInspection: "2027-02-15",
  },
  {
    id: "e1010000-0000-0000-0000-000000000002",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Mô tô nước Sea-Doo Spark 90HP #02",
    compatibilityGroup: "JET_SKI",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-20",
    nextInspection: "2027-04-15",
  },
  {
    id: "e1010000-0000-0000-0000-000000000003",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Cano kéo dù bay Yamaha 200HP #01",
    compatibilityGroup: "SPEEDBOAT",
    capacity: 6,
    status: "INSPECTION_DUE",
    lastMaintenance: "2026-11-06",
    nextInspection: "2026-11-20",
  },
  {
    id: "351d823e-deb5-5174-9ed5-99c6f867330b",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 1 – Mô tô nước – Kajin Vina",
    compatibilityGroup: "demo:2cdfabcf-13a2-550a-8f9e-283aad7053c9",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "7c35ea0c-f84f-581c-afd6-bbd26f950096",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 2 – Mô tô nước – Kajin Vina",
    compatibilityGroup: "demo:2cdfabcf-13a2-550a-8f9e-283aad7053c9",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "d524c36c-02b4-569b-8c1f-71f278890623",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 3 – Mô tô nước – Kajin Vina",
    compatibilityGroup: "demo:2cdfabcf-13a2-550a-8f9e-283aad7053c9",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "b82c0789-efce-5b02-b6b3-a561f0d9d0fe",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 1 – Dù bay trên biển – Kajin Vina",
    compatibilityGroup: "demo:4190046d-112a-5b93-beb2-bcc8b1cd16d4",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "ef9d5861-f320-51ac-a7e4-28f388dff486",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 2 – Dù bay trên biển – Kajin Vina",
    compatibilityGroup: "demo:4190046d-112a-5b93-beb2-bcc8b1cd16d4",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "6a3fc0a1-b39a-5d52-8af9-98e9a49d0a98",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 3 – Dù bay trên biển – Kajin Vina",
    compatibilityGroup: "demo:4190046d-112a-5b93-beb2-bcc8b1cd16d4",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "ddcac471-c89f-563a-bbc0-b9416b0b970e",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 4 – Dù bay trên biển – Kajin Vina",
    compatibilityGroup: "demo:4190046d-112a-5b93-beb2-bcc8b1cd16d4",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "41b8c90c-eb34-56bf-b1cf-a0eb07773d61",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 1 – Phao chuối – Kajin Vina",
    compatibilityGroup: "demo:fd6fced2-812e-56ce-817f-80bb3d0841e9",
    capacity: 5,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },
  {
    id: "ebc3f0e4-5db9-5e8f-9dce-95d7522f7d31",
    vendorId: "baea9605-392b-5003-93f1-60f2c0582f65",
    name: "Demo 2 – Phao chuối – Kajin Vina",
    compatibilityGroup: "demo:fd6fced2-812e-56ce-817f-80bb3d0841e9",
    capacity: 5,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-15",
    nextInspection: "2027-04-15",
  },

  // Danabeach Club (vender_1: 27afde91-58fd-5daa-b9fa-7f93f00135f0)
  {
    id: "e1010000-0000-0000-0000-000000000004",
    vendorId: "27afde91-58fd-5daa-b9fa-7f93f00135f0",
    name: "Mô tô nước Yamaha WaveRunner #01",
    compatibilityGroup: "JET_SKI",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-18",
    nextInspection: "2027-04-18",
  },
  {
    id: "e1010000-0000-0000-0000-000000000005",
    vendorId: "27afde91-58fd-5daa-b9fa-7f93f00135f0",
    name: "Cano kéo phao chuối 150HP #01",
    compatibilityGroup: "SPEEDBOAT",
    capacity: 8,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-22",
    nextInspection: "2027-04-22",
  },

  // Go N Chill SUP Club (vender_8: c7dcff25-f5dc-5fee-a719-5c23b6a899cb)
  {
    id: "e1010000-0000-0000-0000-000000000006",
    vendorId: "c7dcff25-f5dc-5fee-a719-5c23b6a899cb",
    name: "Ván SUP bơm hơi Red Paddle 10.6 #01",
    compatibilityGroup: "SUP",
    capacity: 1,
    status: "MAINTENANCE",
    lastMaintenance: "2026-11-07",
    nextInspection: "2027-03-01",
  },
  {
    id: "e1010000-0000-0000-0000-000000000007",
    vendorId: "c7dcff25-f5dc-5fee-a719-5c23b6a899cb",
    name: "Ván SUP bơm hơi Red Paddle 10.6 #02",
    compatibilityGroup: "SUP",
    capacity: 1,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-25",
    nextInspection: "2027-04-25",
  },

  // Da Nang Outdoor Adventures (DNOA - vender_3: 631db4cf-1ec4-5b06-8f98-56abcf3406d0)
  {
    id: "e1010000-0000-0000-0000-000000000008",
    vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0",
    name: "Ván lướt sóng xốp Torq Surfboard #01",
    compatibilityGroup: "SURFBOARD",
    capacity: 1,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-12",
    nextInspection: "2027-04-12",
  },
  {
    id: "e1010000-0000-0000-0000-000000000009",
    vendorId: "631db4cf-1ec4-5b06-8f98-56abcf3406d0",
    name: "Thuyền Kayak đôi biển Seaflo #01",
    compatibilityGroup: "KAYAK",
    capacity: 2,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-14",
    nextInspection: "2027-04-14",
  },

  // Loca Travel (vender_4: 10f8ffc5-e987-592f-9d38-426ad7f97066)
  {
    id: "e1010000-0000-0000-0000-000000000010",
    vendorId: "10f8ffc5-e987-592f-9d38-426ad7f97066",
    name: "Cano lặn ngắm san hô Bãi Nam 120HP #01",
    compatibilityGroup: "SPEEDBOAT",
    capacity: 10,
    status: "INSPECTION_DUE",
    lastMaintenance: "2026-11-08",
    nextInspection: "2026-11-25",
  },

  // D BLUE / D2Tour (vender_5: 128672c5-94e7-5560-896c-158794913b8c)
  {
    id: "e1010000-0000-0000-0000-000000000011",
    vendorId: "128672c5-94e7-5560-896c-158794913b8c",
    name: "Cano câu cá biển D2Tour 150HP #01",
    compatibilityGroup: "SPEEDBOAT",
    capacity: 8,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-19",
    nextInspection: "2027-04-19",
  },

  // Khuê Nguyễn (vender_6: 2680f19f-8b5a-5aa8-8474-cb13e2ff1c22)
  {
    id: "e1010000-0000-0000-0000-000000000012",
    vendorId: "2680f19f-8b5a-5aa8-8474-cb13e2ff1c22",
    name: "Tàu câu mực đêm chuyên dụng Khuê Nguyễn",
    compatibilityGroup: "FISHING_BOAT",
    capacity: 12,
    status: "OPERATIONAL",
    lastMaintenance: "2026-10-21",
    nextInspection: "2027-04-21",
  },
];

export const VENDOR_USER_MAP: Record<string, string> = {
  "vender_1@danasea.test": "27afde91-58fd-5daa-b9fa-7f93f00135f0",
  "vender_2@danasea.test": "baea9605-392b-5003-93f1-60f2c0582f65",
  "vender_3@danasea.test": "631db4cf-1ec4-5b06-8f98-56abcf3406d0",
  "vender_4@danasea.test": "10f8ffc5-e987-592f-9d38-426ad7f97066",
  "vender_5@danasea.test": "128672c5-94e7-5560-896c-158794913b8c",
  "vender_6@danasea.test": "2680f19f-8b5a-5aa8-8474-cb13e2ff1c22",
  "vender_7@danasea.test": "5fb18fee-16fd-5b9f-bcbd-759b47768d9b",
  "vender_8@danasea.test": "c7dcff25-f5dc-5fee-a719-5c23b6a899cb",
  "vender_9@danasea.test": "5a53c8e5-3a76-5383-b104-17b22d4af50f",
};
