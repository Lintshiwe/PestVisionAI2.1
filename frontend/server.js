const path = require('path')
const express = require('express')
const morgan = require('morgan')
const http = require('http')
const https = require('https')

const app = express()
const port = process.env.PORT ? Number(process.env.PORT) : 3000
const streamUrl = process.env.STREAM_URL || 'http://localhost:8000/video/feed'
const backendBaseUrl = process.env.BACKEND_BASE_URL || 'http://localhost:8082'
const streamTarget = new URL(streamUrl)

app.use(morgan('dev'))
app.use(express.static(path.join(__dirname, 'public')))

app.get('/config.json', (_req, res) => {
  res.json({
    streamUrl: '/stream.mjpg',
    sourceStreamUrl: streamUrl,
    backendBaseUrl,
  })
})

app.get('/stream.mjpg', (req, res) => {
  const client = streamTarget.protocol === 'https:' ? https : http

  const upstreamRequest = client.request(
    {
      protocol: streamTarget.protocol,
      hostname: streamTarget.hostname,
      port:
        streamTarget.port || (streamTarget.protocol === 'https:' ? 443 : 80),
      path: `${streamTarget.pathname}${streamTarget.search}`,
      method: 'GET',
      headers: {
        ...req.headers,
        host: streamTarget.host,
      },
    },
    (upstreamResponse) => {
      const status = upstreamResponse.statusCode || 200
      if (!res.headersSent) {
        res.writeHead(status, {
          ...upstreamResponse.headers,
          'cache-control': 'no-store',
        })
      }
      upstreamResponse.pipe(res)
    }
  )

  upstreamRequest.on('error', (err) => {
    console.error('Stream proxy error:', err.message)
    if (!res.headersSent) {
      res.status(502).send('Stream unavailable')
    } else {
      res.end()
    }
  })

  req.on('close', () => {
    upstreamRequest.destroy()
  })

  upstreamRequest.end()
})

app.listen(port, () => {
  console.log(`Live feed frontend listening on http://localhost:${port}`)
})
