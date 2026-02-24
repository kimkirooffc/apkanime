const { search } = require('../_lib/anichin');
const { sendSuccess, sendError, sendJson } = require('../_lib/response');

module.exports = async (req, res) => {
  if (req.method !== 'GET') {
    return sendJson(res, 405, { success: false, message: 'Method not allowed' });
  }

  try {
    const query = typeof req.query.q === 'string' ? req.query.q : '';
    if (!query.trim()) {
      return sendSuccess(res, []);
    }
    const data = await search(query);
    return sendSuccess(res, data);
  } catch (error) {
    return sendError(res, error);
  }
};
