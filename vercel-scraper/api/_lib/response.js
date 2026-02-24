function sendJson(res, statusCode, payload) {
  res.statusCode = statusCode;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 's-maxage=300, stale-while-revalidate=600');
  res.end(JSON.stringify(payload));
}

function sendSuccess(res, data) {
  sendJson(res, 200, { success: true, data });
}

function sendError(res, error, statusCode = 500) {
  const message = error && error.message ? error.message : 'Internal error';
  sendJson(res, statusCode, { success: false, message });
}

module.exports = {
  sendJson,
  sendSuccess,
  sendError
};
