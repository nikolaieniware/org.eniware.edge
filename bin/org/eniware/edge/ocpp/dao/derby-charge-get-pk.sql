SELECT created, sessid_hi, sessid_lo, idtag, socketid, auth_status, xid, ended, posted
FROM  eniwareedge.ocpp_charge
WHERE sessid_hi = ? AND sessid_lo = ?
