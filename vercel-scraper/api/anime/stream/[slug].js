const { getStream } = require('../../_lib/anichin');
const { sendSuccess, sendError, sendJson } = require('../../_lib/response');

module.exports = async (req, res) => {
  if (req.method !== 'GET') {
    return sendJson(res, 405, { success: false, message: 'Method not allowed' });
  }

  try {
    const slugValue = req.query.slug;
    const slug = Array.isArray(slugValue) ? slugValue[0] : slugValue;
    if (!slug || !String(slug).trim()) {
      return sendJson(res, 400, { success: false, message: 'Slug is required' });
    }

    const data = await getStream(String(slug));
    return sendSuccess(res, data);
  } catch (error) {
    return sendError(res, error);
  }
};
